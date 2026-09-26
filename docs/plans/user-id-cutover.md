# UserId cutover — the overnight brief

Self-contained brief for one long session. Read it whole, then work top to bottom and tick
the boxes in **Progress** after every step (the file is the checkpoint; a session cut off by
the usage limit must leave nothing in its head that is not on disk).

Background and the reasoning: `../analysis/2026-09-26-user-id-as-the-key.md` (long; skim §8–§9).
Decisions already made by the owner — do not reopen them:
- identity = `UserId` (`shared/user-id`, the UUID from `users.id`); e-mail is an attribute;
- token: `sub` = UUID, `email` = its own claim, no `uid`;
- display names come from security's `GET /users?ids=` through `AuthorDirectory`
  (`shared/author-directory`); no nickname exists, the name is the masked address;
- no k8s work, no projection/CDC, no read-only DB view;
- Flyway: one `V1__schema.sql` per service — edit it, never add a numbered file; a changed
  schema means `docker compose -p security down -v` on the local stack.

## Done (2026-09-26)

- security: `User.id: UserId`, `sub` = id + `email` claim, `/me.id`, `GET /users?ids=`
  (masked names, anonymous, throttled), `display-names.feature` (application + HTTP);
- `offline-jwt`: `VerifiedToken.email()`, fallback to an address-as-subject until old tokens expire;
- memes and comments: `author_id` (nullable) written from the token on publish/comment,
  read everywhere, names via `AuthorDirectory` in `/meta` and the thread listing;
  `portal/dev/backfill-author-ids.sh`;
- all five services on one `V1__schema.sql`.

## 1. Finish the cutover (code)

Order matters: each step must build green and be committed **before** the next starts.

- [ ] **1a collections `user_id`.** `SavedItem` gains `Optional<UserId> userId` (overload the old
      constructors like `Comment` did); `CollectionRepository.add(...)` needs the id to reach the
      INSERT — the least invasive shape is `add(SavedItem)` or an extra `Optional<UserId>` parameter,
      pick one and say why in the commit; `V1__schema.sql`: `user_id UUID` + index on
      `collection_items`; `SecurityGate.userFor` returns the id beside the address (or a small
      `Caller` record like memes'); `CollectionsApi` passes it on save. Extend
      `dev/backfill-author-ids.sh` with `collection_items (user_email → user_id)`. Build:
      `portal/mvnw -f portal/microservice-user-collections/pom.xml clean verify`.
- [ ] **1b ownership by id.** memes `own` (`/meta`) and `DeleteMeme`'s "may this caller delete",
      comments `viewerIsAuthor` and `DeleteComment`: compare `authorId` with the caller's id when
      both are present, fall back to the address while either is missing. Tests for both branches.
- [ ] **1c the saga carries the id.** `shared/account-closure`: `Field.USER_ID`, `ClosureCommand.userId`
      (Optional during the dual period), `isAddressed()` = id or e-mail present. Security's
      `AccountDeletionOrchestrator` puts `userId` beside `email` on `ACCOUNT_DELETION_REQUESTED`;
      offboarding copies it onto every command; participants prefer `activeOf(UserId)` /
      `pendingOf(UserId)` and fall back to the address for rows without an id. Every pact between
      security ↔ offboarding ↔ participants is regenerated (consumer side) and re-verified
      (provider side) — the list is in the CI workflows (`*PactProviderTest`, `*ContractTest`).
      Runner: `PortalInOneProcess` addresses the leaver by id; `account-closure.feature` keeps
      its literal address (the step maps it to an id once).
- [ ] **1d anonymisation writes `author_id = NULL`.** `reassignAuthor(id, DeletedAccount.AUTHOR)`
      in memes and comments also clears `author_id`, so kept content of a closed account is not
      groupable by id. One test each.
- [ ] **1e retire the address as a key** — only after 2 below has run the backfill and every
      `still_without_id` is 0 or explained: `NOT NULL` on the id columns, drop the `author`/`voter`/
      `user_email` indexes' role as keys (`activeOf(String)` overloads go), delete the rekey
      machinery (`UserContentRekey`, `RekeyUserContent`, `JdbcUserContentRekey`, the
      `EMAIL_CHANGED` consumers and their pacts, `EmailChangedAnnouncer` in security), then
      `ClosureCommand` v2 without `email`. Big and last; if the session is short on budget,
      stop before this step and leave it ticked "not started".

## 2. Prove it on the real stack

- [ ] `docker compose -p security down -v` (both compose files share the project name).
- [ ] Rebuild images: security (`shared/infra-up.sh` / the identity compose), then
      `portal/memes-up.sh` or `portal/infra-up.sh` (read the script headers; Docker context must be
      `desktop-linux`, see memory "ściągi i porty stacku").
- [ ] Register two users through the UI or Newman, upload a meme, comment, save a favourite;
      check `/memes/{id}/meta` shows `a***@…` from the directory (security logs show `GET /users`).
- [ ] Run `portal/dev/backfill-author-ids.sh`; every table reports `still_without_id = 0`.
- [ ] Nightly gates by hand: `portal/e2e` (Playwright) and the Newman collections in
      `shared/demo/` — green, or the failure fixed in code, never in the test.
- [ ] Close one account end to end (SELF and ADMIN with `comments=ANONYMIZE_AUTHOR`); the kept
      comments render "deleted account".

## Rules for the session

- One session, no subagents, no Fable. Batch edits with a Python script per slice; read only
  what the slice touches.
- Build in the background with the wrapper by absolute path
  (`/home/robert/Documents/git/portal/mvnw -f <pom>`; `../mvnw` does not resolve in a background
  subshell), one build at a time per module tree — two concurrent builds sharing `target/` produced
  a false failure today. Wait with an `until grep BUILD_EXIT` loop, not by polling every minute.
- Commit only after the build is green, **in a separate command** from the build; `set -e` does
  not stop a script in this shell — check the exit code explicitly.
- Every shared-library change: `mvn clean install` locally, then push the library **before** the
  consumers; CI of memes/comments/collections/security builds libraries from checkouts — extend
  both the checkout list and the install loop in their `ci.yml`.
- Dependency analyzer in security: every module that names `UserId` declares `user-id`
  explicitly (test scope where only tests use it).
- Short javadoc, no history in comments (owner's rule). English in every artefact.
- After each ticked box: one line in **Progress** below with the commit hashes.

## Progress

(append lines: `- 1a done — memes 0123abc, portal 4567def`)
