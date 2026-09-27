# Meme deletion as a protocol — the overnight brief

Self-contained brief for one long session. Read it whole, then work top to bottom and tick
the boxes in **Progress** after every step (the file is the checkpoint; a session cut off by
the usage limit must leave nothing in its head that is not on disk).

## Why

Closing an account is a protocol the estate owns: a vocabulary (`shared/account-closure`), a
participant contract (its test-jar), thin per-service participant modules (`*_account-closure`) and
one world in one process (`portal/account-closure-specs`) that proves the protocol without Kafka
or Postgres. Deleting a meme is the estate's second protocol — the choreographed cascade
`MEME_DELETED` → comments drop the thread and announce `COMMENTS_DELETED` → collections drop every
reference to the meme and to the comments — and it has none of that. Today:

- the two message names are string literals in three infrastructures:
  `KafkaMemeEvents.MEME_DELETED` (memes), `KafkaCommentEvents.COMMENTS_DELETED` (comments),
  and `"MEME_DELETED"` / `"COMMENTS_DELETED"` inline in `CascadeConsumer` (collections) and
  `MemesEventsListener` (comments); the field names `memeId`, `commentIds`, `id`/`eventId`,
  `version` are repeated the same way;
- the only guard is `CascadeTopicNamesTest`, hand-copied into comments and collections;
- the hop's decisions live in transport classes: `MemesEventsListener` (comments, ~120 lines)
  decides "an empty thread announces nothing"; `CascadeConsumer` (collections, ~420 lines) decides
  which ids are unusable, what a deletion that names nothing means, and does the polling, the
  3-attempt retry and the give-up in the same file;
- `CommentEvents`, the port comments announces through, is declared in comments-INFRASTRUCTURE
  (`comments-infrastructure/.../CommentEvents.java`), so no application-level test can drive the
  hop end to end — the same leak `RequestedBy` was in offboarding before 2026-09-12;
- the only end-to-end proof is `portal/e2e/features/deletion-cascade.feature` against the live
  stack, nightly.

The owner's word (2026-09-27): build the account-closure equivalent for meme deletion and **do not
break DRY** — one vocabulary, one `UnitOfWork`, one set of in-memory fakes for both protocols.

Decisions already made — do not reopen them:
- names follow the type: `UserId userId`, `MemeDeleted memeDeleted`; the only allowed shorthand is
  a test's single `service`;
- fakes take domain types, never a `String` where the domain has an id; a Gherkin address is
  translated once, in the steps;
- short javadoc (1–2 sentences), no history in comments; English in every artefact;
- topics stay in infrastructure (as `SagaTopics` does): the library carries message names and
  fields, not `memes-events` / `comments-events`;
- no compensation is invented for the cascade: it is choreography, idempotent hops, and a
  documented give-up (`CascadeConsumer.MAX_ATTEMPTS`); the specs state that, they do not fix it.

Decisions taken by this brief (flip by editing the line, before the night starts):
- **D1** `UnitOfWork` moves out of `account-closure` into its own library `shared/unit-of-work`
  (`com.jrobertgardzinski.unitofwork.UnitOfWork`, one interface, no dependencies), so the second
  protocol does not depend on the first for one interface. Alternative rejected: `meme-deletion`
  depending on `account-closure` — a vocabulary of closing accounts is not a dependency of deleting
  memes.
- **D2** the new library is `shared/meme-deletion`, package `com.jrobertgardzinski.deletion`.
- **D3** `portal/account-closure-specs` is renamed `portal/portal-specs`: one world in one
  process, two protocols. The fakes move to package `com.jrobertgardzinski.portal.heap`; each
  protocol keeps its own package, runner and feature file. Alternative rejected: a second specs
  module and a test-jar of fakes — two copies of the same world, or a third module to share it.
- **D4** memes gets no `memes_meme-deletion` module: it only announces, and `DeleteMeme` +
  the `MemeEvents` port already live in memes-application. It only starts speaking the library's
  names.

## 0. `shared/unit-of-work` (D1)

- [x] **0a the library.** `gh repo create jrobertgardzinski/unit-of-work --public`; pom like
      `shared/user-id` (artifactId `unit-of-work`, no dependencies); move
      `account-closure/src/main/java/com/jrobertgardzinski/closure/UnitOfWork.java` to
      `unit-of-work/src/main/java/com/jrobertgardzinski/unitofwork/UnitOfWork.java`, javadoc as is.
      `account-closure` depends on it and drops its own copy; `AtomicParticipantContractTest` imports
      the new package. `mvn clean install` both, in that order.
- [x] **0b registration.** `shared/pom.xml` `<module>unit-of-work</module>` before `account-closure`;
      `shared/estate/shared.repos`; every `ci.yml` that checks out `account-closure` also checks out
      and installs `unit-of-work` BEFORE it: memes, comments, user-collections, offboarding,
      security, portal (`ci.yml` twice, `e2e-saga.yml`), workspace-shared. Run
      `portal/check-workflow-checkouts.sh ../shared` — it must go red before the edit and green after.
- [x] **0c consumers.** memes and comments participants (`*_account-closure`) and the two
      `PurgeCommandsListener`s import the new package. Build each service, commit.
      Push order: unit-of-work → account-closure → services → portal.

## 1. `shared/meme-deletion` — the vocabulary and the hop contract (D2)

- [x] **1a the library.** `gh repo create jrobertgardzinski/meme-deletion --public`; pom like
      `account-closure` (test-jar plugin, allure-junit5, junit), dependency on `unit-of-work` only.
      Main sources, package `com.jrobertgardzinski.deletion`:
      - `DeletionMessages`: `MEME_DELETED`, `COMMENTS_DELETED`; nested `Field`: `TYPE = "type"`,
        `ID = "id"`, `EVENT_ID = "eventId"` (memes writes `eventId`, comments writes `id` — keep both
        names in the vocabulary and note it; unifying the wire is NOT this brief), `MEME_ID = "memeId"`,
        `COMMENT_IDS = "commentIds"`, `VERSION = "version"`;
      - `MemeDeleted(String memeId)`: record; `static Optional<MemeDeleted> of(String memeId)` —
        empty when the id is missing, blank or not a UUID (the check now in
        `CascadeConsumer.isNotAnId`, 36 characters + `UUID.fromString`); `Map<String, Object> fields()`
        like `ClosureConfirmation.fields()`;
      - `CommentsDeleted(String memeId, List<String> commentIds)`: record;
        `static Optional<CommentsDeleted> of(String memeId, List<String> ids)` — empty when the
        meme id is unusable; keeps only usable comment ids and exposes `unusable()` count so the
        adapter can log it (`CascadeConsumer.onCommentsDeleted` today); an empty usable list is a
        valid event that names nothing;
      - `DeletionOutcome`: sealed — `Dropped(int rows)` | `Nothing` — one shape for both hops
        (the closure protocol has three `ClosureOutcome` copies; do not repeat that).
      Tests in the library for `of()` (the three unusable shapes, the partial list).
- [x] **1b the contract, as a test-jar.** `CascadeHopContractTest` (abstract), modelled on
      `ClosureParticipantContractTest`: `handle(MemeDeleted)` / `handle(CommentsDeleted)`,
      `givenMemeHas(int rows)`, `nothingTouched()`; the promises every hop owes the cascade:
      a redelivered event drops nothing and announces nothing (ADR 0006 idempotence); an event that
      names nothing is dropped without touching anything; an event of another type is not this hop's
      business. `AtomicHopContractTest` adds what `AtomicParticipantContractTest` adds: the
      announcement is made inside `unitOfWork.run()`'s step — only comments announces, collections
      does not, so only comments extends it.
- [x] **1c registration** as in 0b (aggregator, estate list, the ci.yml of memes, comments,
      user-collections, portal ×3, workspace-shared; not offboarding, not security).

## 2. comments — the first hop as a participant

- [x] **2a the port comes home.** Move `CommentEvents` from comments-infrastructure to
      comments-application (`com.jrobertgardzinski.comments.application.CommentEvents`), keep
      `KafkaCommentEvents` and `NoopCommentEvents` as its adapters.
- [x] **2b `comments_meme-deletion`.** Module beside `comments_account-closure`, `src/main` only,
      dependencies: comments-application, comments-domain, meme-deletion, unit-of-work, slf4j.
      `CommentsDeletionParticipant(DeleteThread deleteThread, CommentEvents commentEvents,
      UnitOfWork unitOfWork)` with `DeletionOutcome handle(MemeDeleted memeDeleted)`: inside
      `unitOfWork.run`, `deleteThread.execute(memeId)`; announce `commentsDeleted(memeId, dropped)`
      only when `dropped` is not empty (the rule now in `MemesEventsListener.dropTheThreadAndAnnounceIt`).
- [x] **2c the listener shrinks.** `MemesEventsListener` keeps: MDC/cid, JSON parsing, the
      "malformed → drop" log, `DeletionMessages.MEME_DELETED` dispatch, `MemeDeleted.of(...)`, the
      participant call, the info log. `KafkaCommentEvents` builds its payload from
      `CommentsDeleted.fields()` + `DeletionMessages.Field.*`. `CascadeTopicNamesTest` stays (topics
      are transport). `MemeDeletedCascadeTest` / `MemeDeletedContractTest` keep testing the wire; the
      hop's own promises move to portal-specs (step 5) — as with the closure participants, the
      service's CI stops testing the participant, the portal's does.
- [x] **2d** `comments/pom.xml` module list, `comments/.github/workflows/ci.yml` (checkouts +
      install order: unit-of-work, meme-deletion). Build the whole service, commit, push after 1c.

## 3. user-collections — the second hop as a participant

- [x] **3a `collections_meme-deletion`.** `CollectionsDeletionParticipant(PurgeDeletedItem
      purgeDeletedItem)` with `handle(MemeDeleted)` → `purge("meme", [memeId])` and
      `handle(CommentsDeleted)` → `purge("comment", commentIds)`; returns `Dropped(removed)` /
      `Nothing`. The item-type constants move here from `CascadeConsumer` (`MEME_ITEM_TYPE`,
      `COMMENT_ITEM_TYPE`).
- [x] **3b `CascadeConsumer` keeps the transport only:** polling, `MAX_POLL_RECORDS`, the
      3-attempt retry and the give-up log, cid header, the topic → type dispatch. Everything under
      `onMemeDeleted` / `onCommentsDeleted` / `isNotAnId` goes through `MemeDeleted.of` /
      `CommentsDeleted.of` and the participant; the "unusable ids" warning reads `unusable()`.
      `CascadeConsumerTest` / `CascadeConsumerLoopTest` keep the loop and the retry; assertions
      about which ids are purged move to the participant test in portal-specs.
- [x] **3c** module list, `ci.yml`, build, commit, push.

## 4. memes — speaks the vocabulary

- [x] **4a** `KafkaMemeEvents` builds the payload from `MemeDeleted.fields()` +
      `DeletionMessages` (keep `eventId` on the wire — the pacts pin it), `MemeDeletedTopicTest`,
      `MemeDeletedPactProviderTest`, `CommentsMemeDeletedPactProviderTest` re-verified unchanged.
      memes-infrastructure depends on meme-deletion; `ci.yml`, build, commit, push.

## 5. portal-specs — one world, two protocols (D3)

- [x] **5a rename.** `git mv account-closure-specs portal-specs`; artifactId `portal-specs`;
      `portal/pom.xml` module; `portal/specs/README.md` and the module's own README (if none,
      write one paragraph: what runs here, what does not). `.system-review/` mentions are history,
      leave them.
- [x] **5b the fakes move to `com.jrobertgardzinski.portal.heap`:** `HeapMemes`, `HeapComments`,
      `HeapFavourites`, plus `Identities.idOf(String email)` (today `PortalInOneProcess.idOf`).
      `HeapMemes` gains what the cascade needs through the port it already implements
      (`MemeRepository.deleteById`) — nothing new; `HeapComments` already implements
      `CommentRepository.deleteByMeme`; `HeapFavourites` already is `ItemReferences`
      (`InMemoryCollectionRepository.purge`). Readers the deletion specs will call by name:
      `HeapComments.under(String memeId)`, `HeapFavourites.pointingAt(String itemType, String id)`.
- [x] **5c the world splits from the bus.** `portal.heap.Portal`: the three heaps, the use cases
      of all three services (today built inline in `PortalInOneProcess`) and both protocols'
      participants; `portal.closure.ClosureInOneProcess` = today's `PortalInOneProcess` minus the
      heaps (the router, `deliver`, `answerOf`, `saidToSecurity`); `portal.deletion.DeletionInOneProcess`:
      an in-memory `MemeEvents` and `CommentEvents` that put each announcement in an `inFlight`
      list; `everyHopAnswers()` drains it: `MEME_DELETED` → comments participant AND collections
      participant, `COMMENTS_DELETED` → collections participant; `silence("comments")` as in the
      closure world; `redeliver()` re-sends the last `MEME_DELETED`.
- [x] **5d `portal/specs/meme-deletion.feature`** (business language, personas as in the closure
      feature, the mechanics in `#` comments), runner `MemeDeletionSpecsTest`, glue package
      `com.jrobertgardzinski.portal.deletion`. Scenarios, in this order:
      1. an author takes a meme down: the thread is gone, and so is every favourite that pointed at
         the meme or at a comment under it;
      2. a meme nobody commented on: collections hears one hop only (no empty `COMMENTS_DELETED`);
      3. the same deletion arrives twice: nothing changes and nothing is announced;
      4. the comments part never hears: favourites of the meme go, favourites of its comments stay,
         and nothing ever compensates — the thread stays as dead rows (this is the truth of the
         choreography, ADR-worthy, not a bug to fix here);
      5. a deletion that names no meme is dropped by every hop.
- [x] **5e participant tests on the contract**, beside the closure ones:
      `deletion/CommentsDeletionParticipantTest extends AtomicHopContractTest`,
      `deletion/CollectionsDeletionParticipantTest extends CascadeHopContractTest`, on the heaps,
      no mocks. Build: `portal/mvnw -f portal/portal-specs/pom.xml clean test`.
- [x] **5f CI.** `portal/.github/workflows/ci.yml` and `e2e-saga.yml`: checkouts and install order
      for unit-of-work and meme-deletion; `check-workflow-checkouts.sh` green; the specs job name
      follows the module. Commit, push.

## 6. Prove it

- [x] **6a** every touched repo: `clean verify` from the repo root, green, then pushed in the order
      unit-of-work → account-closure → meme-deletion → comments → user-collections → memes →
      offboarding/security (0c only) → portal → workspace-shared. Wait for the CI of each library
      before pushing its consumers.
- [ ] **6b on a machine with Docker only:** `docker compose -p security down -v`, `infra-up.sh`,
      `portal/e2e` `deletion-cascade.feature` and `account-deletion.feature` green. Without Docker,
      write "6b not run — no Docker on this machine" in Progress; the nightly e2e is the gate.
- [x] **6c** `shared/docs/features.md` catalogue: the new feature file and the two contracts;
      one paragraph in `portal/README.md` where the closure protocol is described, saying the
      deletion cascade is the second protocol built the same way.

## Rules for the session

- One session, no subagents. Batch edits with a Python script per slice; read only what the slice
  touches.
- Build with the wrapper by absolute path (`/home/robert/Documents/git/portal/mvnw -f <pom>`), one
  build at a time per module tree; check the exit code explicitly — `set -e` does not stop a
  script in this shell.
- `mvn -pl <module>` from a workspace root runs nothing (it selects the aggregator); enter the
  service directory or add `-amd`.
- A local `mvn test` without `clean` lies after a rename (stale classes in `target/`); a green
  build against a stale jar in `~/.m2` lies too — after every library change, `mvn clean install`
  it before building its consumers (this bit the specs on 2026-09-27: `DeletedAccount` was gone
  from memes and the specs stayed green on the old jar).
- Every shared-library change: install locally, push the library BEFORE its consumers; CI of the
  services builds libraries from checkouts — extend both the checkout list and the install loop,
  in dependency order, and run `check-workflow-checkouts.sh`.
- Commit only after the build is green, in a separate command from the build. English commit
  messages, the attribution lines the session is given.
- After each ticked box: one line in **Progress** with the commit hashes.

## Progress

(append lines: `- 0a done — unit-of-work 0123abc, account-closure 4567def`)

- Before 0a: the Atomically -> UnitOfWork rename WAS done and pushed from the other machine
  (account-closure 36f076c, memes 9270ddd, comments a077f29) — this machine had not fetched the
  sub-repositories, only the two workspaces, so the brief's "move UnitOfWork.java" named a file
  that was still called Atomically here. Fetched all 33 repos first; the local duplicate rename
  was rebased away. Note for the next session: `git -C <workspace> pull` leaves every
  sub-repository untouched.
- Also before 0a: portal/main did not compile. 2aa3565 changed the specs' participant tests to
  pass `unitOfWork`, which account-closure only gained in 36f076c, pushed ten minutes later and
  never built together. 0a's move fixes it; account-closure-specs is green again.
- 0a done — unit-of-work bffa786 (new repo, CI green), account-closure 74ea82b. UnitOfWork keeps
  its one method; the javadoc is protocol-neutral now ("everything the step does commits
  together"), because the closure-specific wording would be wrong in a library both protocols use.
- 0b done — shared 74960a8 (aggregator, estate/shared.repos, .gitignore, its own ci.yml), portal
  4129ef7 (ci.yml x2 jobs, e2e-saga.yml). check-workflow-checkouts.sh: red before
  (20 modules, workflows short of 1), green after.
- 0c done — memes 0debb5b, comments ee66a3a, user-collections 764b5fa, offboarding 66c0f5d,
  microservice-security e5d3670. The PurgeCommandsListeners needed no change: both pass a
  lambda, so the port's name never appears there. Builds green; account-closure-specs green.
  Pushed unit-of-work -> account-closure -> services -> portal -> workspace-shared;
  account-closure has no CI of its own (the reactor in workspace-shared validates it).

- 1a/1b/1c done — meme-deletion bffa786..(repo created, CI green): DeletionMessages, MemeDeleted,
  CommentsDeleted, DeletionOutcome, and the two contracts as a test-jar; 9 library tests green.
  Two deviations from the brief, both small: CommentsDeleted is a three-component record
  (memeId, commentIds, unusable) because a record cannot expose unusable() otherwise; and
  fields() is insertion-ordered rather than Map.copyOf, because memes builds its payload by hand
  and the outbox stores it verbatim. Registered in shared 74960a8+, portal 4129ef7+.
  The contract could not be stated exactly as the brief drew it: "an event of another type is
  not this hop's business" cannot be a shared test when COMMENTS_DELETED *is* collections'
  business. It is a test of the comments hop instead; the shared contract keeps idempotence,
  "drops what the deletion names", and "an event that names nothing touches nothing".
- 2a-2d done — comments 355bd29. 161 tests green. One behaviour changed on purpose: this hop had
  NO id check at all, so the cucumber harness and the MEME_DELETED pact used "known-meme" as a
  meme id. MemeDeleted.of turns that away, which is the rule the collections hop already
  enforced; both fixtures now use a real uuid and the pact file was regenerated.
- 3a-3c done — user-collections ffcfbb4. 172 tests green. CascadeConsumer lost ~90 lines of
  decisions and kept the loop; MEME_ITEM_TYPE/COMMENT_ITEM_TYPE moved to the participant.
- 4a done — memes 0f0a73a. 230 tests green (`-pl '!memes-ui'`), the three MEME_DELETED pact and
  topic tests re-verified unchanged. NOTE: memes-ui/src/gallery.test.tsx fails on this machine on
  a CLEAN checkout too — pre-existing, unrelated, not touched.
- 5a-5f done — portal 6d86044. 57 tests in portal-specs, up from 41. Five deletion scenarios, and
  the two hops held to the library's contract on the heaps with no mocks. The rename cost a
  package move (the heaps to portal.heap) and Identities.idOf; the closure specs are unchanged in
  behaviour.
- 6a done — every touched repo `clean verify` green and pushed in the order unit-of-work ->
  account-closure -> meme-deletion -> services -> portal -> workspace-shared, each library's CI
  waited for (unit-of-work green, meme-deletion green; account-closure has no CI of its own).
  Whole shared kernel and whole portal reactor build clean.
- 6b NOT RUN — no Docker on this machine. The nightly e2e is the gate for deletion-cascade.feature
  and account-deletion.feature.
- 6c done — docs/features.md regenerated (290 scenarios, the new feature listed), portal/README.md
  gained "Two protocols the portal owns", portal/specs/README.md and the new portal-specs/README.md
  say what runs where.
