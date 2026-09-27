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
- [x] **1e retire the address as a key** — only after 2 below has run the backfill and every
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

## 3. Guard the retired address (a build that goes red when the key creeps back)

Why this is section 3: the Progress log's last line says it outright — "no source-level guard for
the dropped columns" — and analysis §9 stage 7 names the shape to copy (`MemeReadFilterTest`, the
SQL-literal scan with a one-entry exemption list and a counterweight assertion). The estate is clean
today (grep over java+sql, 2026-09-27: zero hits for `user_email` / `EMAIL_CHANGED` / `Rekey`), but
one dormant address-keyed read survived 1e: `CommentRepository.findByAuthor(String)` →
`WHERE author = ?` (`portal/microservice-comments/comments-application/.../CommentRepository.java:23`,
`comments-infrastructure/.../JdbcCommentRepository.java:67-70`), no production caller — exactly the
"honestly-written SELECT" a guard exists to catch.

No schema change and no shared-library change in 3a–3d: no `down -v`, no install ordering. 3e is the
one exception and is gated on the owner's word. Order matters: 3a before 3b, or the guard is born
with an exemption for dead code. Do not reopen the recorded decisions — `author_id` stays nullable
(NULL = anonymised), `voter` keeps holding the id in its wire form, the Ballots API stays untouched;
the guards below whitelist those on purpose.

- [x] **3a delete the dormant address read in comments.** `findByAuthor(String)` leaves the port
      (`CommentRepository.java:23`), the adapter (`JdbcCommentRepository.java:67-70`) and every fake
      that implements it: comments-application `HideCommentRaceTest:37`, `VoteOnCommentRaceTest:38`,
      `ListCommentsDegradationTest:50`, `PurgeAndCascadeTest:53`, `IdempotentCommandsTest:63`,
      comments-infrastructure `TransactionalDecoratorsTest:78`, and `HeapComments.java:114` in
      account-closure-specs. Builds, in this order:
      `/home/robert/git/portfolio/portal/mvnw -f /home/robert/git/portfolio/portal/microservice-comments/pom.xml clean verify`,
      then `/home/robert/git/portfolio/portal/mvnw -f /home/robert/git/portfolio/portal/account-closure-specs/pom.xml clean verify`.
      Green = both pass with the method gone from every source; commit comments and the specs repo
      separately, each after its own green.
- [x] **3b `RetiredAddressKeyTest` in memes and comments** (`memes-infrastructure` and
      `comments-infrastructure` `src/test/java`, copy `MemeReadFilterTest`'s shape: string literals
      only, never prose, plus a counterweight — assert the scan saw at least one SQL literal, so an
      empty directory cannot go green). Forbidden in main-source SQL literals: `author` as a
      predicate (`WHERE`/`AND author =` — `author_id` is a different word); SELECT lists and writes
      (`INSERT`, `UPDATE … SET author` — `reassignAuthor` writes the placeholder) stay legal, the
      address is an attribute there. Forbidden anywhere in main sources, identifiers included:
      `Rekey`, `EMAIL_CHANGED`. The same test also reads the service's own `V1__schema.sql`: no
      `user_email`, no index on bare `author`. Whitelist, with the reason in the javadoc: `voter`
      (the id in wire form, 1e decision) and `settings.updated_by` (audit snapshot, analysis D8).
      Builds: the memes and comments poms as in 3a. Green = both `clean verify` pass AND the guard
      proven to bite — seed one forbidden literal, watch the red, revert before committing.
- [x] **3c the same guard in collections.** One test in `collections-infrastructure` scanning the
      main tree's string literals (the plain-JDBC SQL is wired from `Main.java`, so scan the whole
      module) and `V1__schema.sql` for `user_email`, `Rekey`, `EMAIL_CHANGED`. Build:
      `/home/robert/git/portfolio/portal/mvnw -f /home/robert/git/portfolio/portal/microservice-user-collections/pom.xml clean verify`.
      Green = verify passes and the seeded-literal check bit once.
- [x] **3d the producer stays dead in security.** A guard test in `security-infrastructure`: no main
      source names `EMAIL_CHANGED` or `EmailChangedAnnouncer`, and `SecurityEventPacts` declares no
      email-changed interaction. Build:
      `/home/robert/git/portfolio/shared/mvnw -f /home/robert/git/portfolio/shared/microservice-security/pom.xml clean verify`.
      Green = verify passes; commit in the security sub-repo after green.
- [x] **3e the address itself on content rows** (the owner said drop it, 2026-09-27). `memes.author` and
      `comments.author` are still `varchar(255) NOT NULL` holding the caller's address (the V1
      comment calls it "the address as an attribute"), while analysis §4/§6 promised the content
      DBs would hold *less* PII after stage 7. Either bless keeping it (then ADR 0008 in 4a records
      why, and 3b's whitelist is the guard) or drop it: edit both `V1__schema.sql` (column + the
      `active_memes`/`active_comments` views — one file per service, never a numbered migration),
      remove the address from `MemeMetadata`/`Comment` and the controllers' writes, rethink
      `reassignAuthor`'s placeholder, then `docker compose -p security down -v` and a step-2 re-run
      on the rebuilt stack. Builds: the memes and comments poms as in 3a, then account-closure-specs.
      Green = all three verify, and the live-stack scenario from section 2 repeats clean.

## 4. Write down the identity that now exists (an ADR, and the READMEs that still teach the old one)

Why this is section 4: no ADR records "UserId is the identity, the e-mail is an attribute" — the
ADR shelf stops at 0007 and the analysis doc's own header says "nothing decided" — while three
service READMEs still present the deleted machinery as today's behaviour: memes `README.md:121-130`
("their content moves with it… announces `EMAIL_CHANGED`… `RekeyUserContent`"), comments
`README.md:64-73`, collections `README.md:57-67` ("keyed by the address…
`collection_items.user_email`, the JWT's `sub`"), and offboarding `README.md:87-88` pins the pacts
as "(id, email…)" / "(type, email)" though v2 carries no email. A newcomer who follows the house
rule and reads the README first re-learns the address as the key — docs that lie rot fastest.

Docs only: no schema, no library, no `down -v`. English, short, one commit per sub-repo, each after
its own proof is green.

- [x] **4a ADR 0008** — `shared/docs/adr/0008-user-id-is-the-identity-email-is-an-attribute.md`,
      the shape of 0001–0007 (context, decision, consequences). It records the brief's header
      verbatim as the decision: `sub` = the UUID from `users.id`, `email` its own claim (no `uid`);
      display names via `AuthorDirectory` ↔ security's `GET /users?ids=` (masked, anonymous,
      throttled); `author_id NULL` = anonymised, kept content not groupable; `ClosureCommand` v2
      keyed by `userId` alone; ballots by the voter's id in wire form; rejected alternatives named:
      `uid` claim, projection/CDC, read-only DB view. Add one dated amendment note to 0007 where its
      reaper query still reads `WHERE author = ?` (`0007:47`), pointing at 0008 — a note, never a
      rewrite of a decided record. If 3e drops (or blesses) the `author` column, 0008 says which.
      Proof: 4d's grep; commit in workspace-shared.
- [x] **4b the three content READMEs.** Replace the "a member's address can move" passages —
      memes `README.md:121-130`, comments `README.md:64-73`, collections `README.md:57-67` — with
      what is true: rows keyed by `author_id`/`user_id`; a rename touches no content row and no
      Kafka loop exists for it; names are fetched at read time through `AuthorDirectory` (60 s
      cache, degradation logs "author names unavailable" and the page still reads); "deleted
      account" = `author_id NULL` or a directory miss. The `reserved` /
      `purge_reserved_nothing` paragraphs stay — still true. Proof: 4d's grep over each repo;
      three commits, one per sub-repo.
- [x] **4c offboarding's contracts paragraph** (`portal/microservice-offboarding/README.md:87-88`):
      describe the pinned fact and confirmations as keyed by `userId`, and that a fact without one
      is refused — verify the exact field list against `shared/account-closure`'s `ClosureMessages`
      and the committed `pacts/` before writing, the README must quote the contract, not the memory
      of it. Proof: 4d's grep; one commit in the offboarding sub-repo.
- [x] **4d regenerate and sweep.** Regenerate the generated docs from workspace-shared
      (`shared/build_features.py` → `docs/features.md`, `shared/build_c4.py` →
      `docs/c4-architecture.md`) and run the sweep:
      `grep -rn "EMAIL_CHANGED\|Rekey\|user_email" */README.md shared/docs portal/*.md` from
      `/home/robert/git/portfolio`. Green = hits only in `docs/plans/`, `docs/analysis/` and the
      historical `PLAN-*`/`PODRECZNIK*`/`AUDYT*` files (history may say what was) — nothing that
      claims to describe the present. `onboarding-guide.md`, `go-live-2026.md` and `todo.md` were
      checked clean on 2026-09-27; re-run the grep, do not re-edit what is not wrong.

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
- 1e code DONE, all six green locally (not pushed yet): account-closure 2eb06b7, memes 47282a3, comments c1b4c78, collections 0d1d410, offboarding e3070dc, security 08d0a7c, portal 7f2ae68 (specs + backfill); stack being rebuilt for the step-2 re-run
- 1e DONE and proven on the rebuilt stack (2026-09-27): scenario OK, backfill memes 0 / comments 0 (collections no longer backfilled), e2e-saga 4/4, Playwright 18/18, notebook OK, SELF and ADMIN closures OK ('deleted account', author_id NULL). Everything pushed. Left deliberately: memes/comments author_id stays nullable (NULL = anonymised); ballots keyed by the voter's id in wire form (Ballots API unchanged); no source-level guard for the dropped columns.
- sections 3 and 4 written (2026-09-27), derived from the estate's own state, not from the owner's
  words: the owner asked for "points 3 and 4" and the brief had none, and analysis §9's stages 3–4
  (projection, `USER_PROFILE_CHANGED`) are the ones this brief's header rejects. Section 3 comes
  from the Progress line above that records no source-level guard; section 4 from an ADR shelf that
  stops at 0007 and three READMEs that still teach the address as the key. 3e (the `author` column
  itself) stays unticked: it needs the owner's word, the way 1e was gated on step 2.
- 3a done — comments fd19d8c (port, adapter and seven fakes; the rollback test now asks the row
  for the leaver's address AND id, which is the stronger assertion), portal 59b38d8 (HeapComments)
- 3b done — comments 76217e1, memes 8b8c05e. Proven to bite in comments: a seeded WHERE author
  predicate, a seeded Rekey identifier and a seeded user_email column each failed exactly one rule,
  the counterweight stayed green, all three seeds reverted.
- 3c done — collections aa542d3 (the predicate rule also names email / user_address, so the column
  cannot come back under another name; user_email alone was already covered by the machinery rule)
- 4a done — shared 2babcb0 (ADR 0008 + a dated note on 0007), corrected by 52b5145: the first commit
  rewrote 0007's reaper query, which an amendment note exists to avoid. The note now covers both
  queries the cutover moved, and 0007 keeps the words it was decided with.
- 4b done — memes 47d94b3, comments 3def8cf, collections ad1da6c + d064b4c (collections shows no
  names at all: unlike its siblings it has no AuthorDirectory)
- 4c done — offboarding 9985e5f, written from the committed pacts: the confirmation is
  (sagaId, type, userId) with no address, the fact is (sagaId, id, userId, email, initiatedBy).
- 3d done — security a6e0ebc. The guard is narrower than "no EMAIL_CHANGED in security": the words
  are legal in exactly one file, ConfirmEmailChangeController, where they are the HTTP reply to the
  browser that confirmed the change, and that file may not name the facts topic. Pinned as a set, so
  a new sayer and a vanished reply both fail. Two counterweights (the deletion fact's pact, and
  something still publishing to security-events).
- 4d done — features.md regenerated (shared 40d0904); sweep over */README.md, shared/docs and
  portal/*.md is clean: the only hits naming the retired machinery are docs/plans, docs/analysis,
  the ADRs that explain it and portal/SYSTEM-REVIEW.md, a dated review record. build_c4.py was NOT
  re-run: it reads a docker-compose.yml beside itself and the estate's identity stack is
  docker-compose.identity.yml, so the generator cannot run in this layout — pre-existing, and the
  owner already parked the C4 refresh in todo.md:130 ("odświeżenie potem może"). c4-architecture.md
  greps clean, so it claims nothing false meanwhile.
- Local build notes (2026-09-27), none of them caused by 3/4 and none of them CI-visible:
  this machine's checkouts were 4-13 commits behind origin and user-id / author-directory were not
  cloned at all, so the estate had to be synced and the shared libraries installed before anything
  compiled (offline-jwt was a commit behind too, which is why VerifiedToken.email() was missing).
  Two tests fail here and pass in CI on the same SHA (security CI green on 08d0a7c):
  CorsPreflightTest.an_unknown_origin_is_refused (FORBIDDEN expected, METHOD_NOT_ALLOWED here) and
  TrustedProxyHttpTest.distinct_clients_are_distinct_sources (TOO_MANY_REQUESTS here) — both fail in
  isolation, both untouched by this work. memes-ui's gallery.test.tsx "still holds every page that
  was loaded after a vote from inside a meme" times out at 5s (8s here, 35/36 green) on Node 22 and
  with no other build running; memes-infrastructure's guard was proven by installing the jars and
  verifying that module. Nothing was "fixed" in any of those tests.
- STILL OPEN for the owner: 3e (the address column on memes/comments rows) is unticked on purpose.
  Also unresolved from 1e's deliberate deviations: whether author_id should stay nullable and
  whether the ballots' wire-form voter id deserves a typed API.
- Found while proving 3/4, and not in either section: **memes' and comments' CI had been red since
  1d** (2026-09-26 18:15) and nobody saw it — the shared-library loop in their `ci.yml` installed
  `account-closure` before `user-id`, so every run died in "Install the shared libraries" with
  "Could not find artifact user-id" once the closure vocabulary started speaking UserId. The
  checkout list was already right; only the order was wrong, and user-collections happened to have
  it the other way round, which is why one of the three stayed green. Fixed: memes 7b7096d,
  comments 5e1110d. All four participant workflows now install user-id before account-closure.
  CI green afterwards on all four repos touched here (shared 05e9740, security a6e0ebc,
  memes 7b7096d, comments 5e1110d) plus collections d064b4c, offboarding 9985e5f, portal 59b38d8.
  Worth the owner's word: `check-workflow-checkouts.sh` guards the checkout LIST but not the install
  ORDER, which is what cost a day of silent red — a guard there is new scope, not done.
- 3e done — the owner's word was "drop it". `memes.author` and `comments.author` are gone from the
  schema, from `Meme`/`MemeMetadata`/`Comment`, from every INSERT and SELECT, and from the views;
  `reassignAuthor(id, placeholder)` became `anonymise(id)` (the row keeps no trace of whose it was);
  `DeletedAccount` is deleted in both services, because "deleted account" is now decided by the
  reader from a null id, not stored; the upload and comment rate-limit buckets follow the id too, so
  a rename no longer resets them; both guards assert the column cannot come back. ADR 0008 records
  the decision instead of the question.
- Found while pricing 3e, and fixed first: **tagging a meme still authorised by ADDRESS**
  (`TagMeme` compared `memes.author` with the caller's address while 1b moved `own` and deletion onto
  the id). Since 1e deleted the rekey machinery, that was permanent: a renamed author was refused
  their own meme, and whoever registered the freed address could rewrite its tags. `OwnershipByIdTest`
  now pins all three decisions — `own`, delete, tags — and the tagging pair was proven to bite.
- NOT PROVEN HERE, and it must be before this is trusted: **the stack re-run that a schema change
  owes** (`docker compose -p security down -v`, `infra-up.sh`, the scenario, the backfill, both
  closures). This machine has no Docker at all (`/var/run/docker.sock` missing — it is the owner's
  notes laptop), so 3e rests on the module suites (memes, comments, account-closure-specs, all green
  on H2) and on CI. Run step 2 on the dev machine before calling the cutover finished.
