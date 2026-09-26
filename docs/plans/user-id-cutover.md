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

- [x] **1a collections `user_id`.** `SavedItem` gains `Optional<UserId> userId` (overload the old
      constructors like `Comment` did); `CollectionRepository.add(...)` needs the id to reach the
      INSERT — the least invasive shape is `add(SavedItem)` or an extra `Optional<UserId>` parameter,
      pick one and say why in the commit; `V1__schema.sql`: `user_id UUID` + index on
      `collection_items`; `SecurityGate.userFor` returns the id beside the address (or a small
      `Caller` record like memes'); `CollectionsApi` passes it on save. Extend
      `dev/backfill-author-ids.sh` with `collection_items (user_email → user_id)`. Build:
      `portal/mvnw -f portal/microservice-user-collections/pom.xml clean verify`.
- [x] **1b ownership by id.** memes `own` (`/meta`) and `DeleteMeme`'s "may this caller delete",
      comments `viewerIsAuthor` and `DeleteComment`: compare `authorId` with the caller's id when
      both are present, fall back to the address while either is missing. Tests for both branches.
- [x] **1c the saga carries the id.** `shared/account-closure`: `Field.USER_ID`, `ClosureCommand.userId`
      (Optional during the dual period), `isAddressed()` = id or e-mail present. Security's
      `AccountDeletionOrchestrator` puts `userId` beside `email` on `ACCOUNT_DELETION_REQUESTED`;
      offboarding copies it onto every command; participants prefer `activeOf(UserId)` /
      `pendingOf(UserId)` and fall back to the address for rows without an id. Every pact between
      security ↔ offboarding ↔ participants is regenerated (consumer side) and re-verified
      (provider side) — the list is in the CI workflows (`*PactProviderTest`, `*ContractTest`).
      Runner: `PortalInOneProcess` addresses the leaver by id; `account-closure.feature` keeps
      its literal address (the step maps it to an id once).
- [x] **1d anonymisation writes `author_id = NULL`.** `reassignAuthor(id, DeletedAccount.AUTHOR)`
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

- [x] `docker compose -p security down -v` (both compose files share the project name).
- [x] Rebuild images: security (`shared/infra-up.sh` / the identity compose), then
      `portal/memes-up.sh` or `portal/infra-up.sh` (read the script headers; Docker context must be
      `desktop-linux`, see memory "ściągi i porty stacku").
- [x] Register two users through the UI or Newman, upload a meme, comment, save a favourite;
      check `/memes/{id}/meta` shows `a***@…` from the directory (security logs show `GET /users`).
- [x] Run `portal/dev/backfill-author-ids.sh`; every table reports `still_without_id = 0`.
- [x] Nightly gates by hand: `portal/e2e` (Playwright) and the Newman collections in
      `shared/demo/` — green, or the failure fixed in code, never in the test.
- [x] Close one account end to end (SELF and ADMIN with `comments=ANONYMIZE_AUTHOR`); the kept
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
- 1a done — collections 6310d8c, portal be6315c (add(user, Optional<UserId>, collection, ref) + default overload)
- 1b done — memes 150be8c, comments 6635da1
- 1c done — account-closure b6b4d13 (pushed), memes 8bc4d09, comments a41e409, collections 33a5bad, offboarding 68c90f4, security 0d6c3be, portal af2a863; pacts regenerated + provider-verified (offboarding 4+4+3, security 2)
- 1d done — memes d2e479b, comments 25d1cb6, portal 2d4c9e2
- 1e not started — gated on step 2's backfill (still_without_id must be 0 first)
- step 2: down -v, infra-up.sh (21/21 healthy), scenario on the live stack OK (/meta = c***@…, security log 2× GET /users), backfill: memes 0 / comments 0 / collection_items 0 still_without_id
- step 2 gates: e2e-saga 4/4, memes-ui Playwright 18/18 (Node 22 from nvm — v20 on PATH is refused by cucumber 13, CI uses 22), demo notebook OK via `jupyter execute` (`python -m nbclient` is not runnable); closures: SELF bob (comment+favourites gone, sign-in refused, farewell mail), ADMIN carol with comments=ANONYMIZE_AUTHOR (kept comment renders 'deleted account', author_id NULL)
- 1e IN PROGRESS (session cut off by the usage limit, 2026-09-26 evening). Decisions taken: ballots keyed by the voter's id in wire form (Ballots API untouched); memes/comments author_id stays NULLABLE (NULL = anonymised, from 1d) — NOT NULL only on collections.user_id; a token without an id is nobody to the filters; offboarding refuses a fact without userId and matches confirmations by userId.
  - DONE + committed (not pushed): account-closure v2 2eb06b7 (command/confirmation by UserId, no email), memes 47282a3, comments c1b4c78 (both green: rekey machinery deleted, ownership/erasure/ballots by id, security pact files deleted).
  - DONE, uncommitted, compiles offline: offboarding (confirmations by userId, commands without email, consumer pacts regenerated in pacts/ — full build NOT run yet).
  - HALF-APPLIED, does not compile: collections — main sources rewritten (SavedItem/CollectionRepository/ItemErasure/JdbcItemErasure/JdbcCollectionRepository/Caller/JwtSecurityGate/CollectionsApi/PurgeCommandsConsumer/participant keyed by UserId; rekey files + pact + 3 tests deleted); Main.java still has `watchedRekey`/`rekeyConsumer` references (lines ~308-367, 399, 414); schema, InMemoryCollectionRepository, ItemErasureContractTest, TestUsers helper and the test regex pass NOT applied — the script scratchpad/slice1e-collections.py from `def main(s):` onward is the remaining work (fix the extra `.alive(aliveStall)` branch first).
  - NOT STARTED: security (delete EmailChangedAnnouncer, its use in ConfirmEmailChangeController, EmailChangeIsAnnouncedTest, SecurityEventPacts.anEmailChangedFact, the three *FactsPactProviderTest), specs (HeapMemes/HeapComments/HeapFavourites heldBy/visibleOf via idOf, participant tests: givenLeaverHolds with the UserId LEAVER, drop givenLeaverHoldsUnderId, PortalInOneProcess confirmations by userId), backfill script (drop collection_items line), full builds in order collections → offboarding → security → specs, then step 2 again on the stack, then push all.
  - Points 3 and 4 the owner asked for do not exist in this plan (only sections 1 and 2); the analysis doc §9 stages 3–4 are superseded by the decisions above — needs the owner's word on what 3 and 4 mean.
