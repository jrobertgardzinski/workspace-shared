# ADR 0006: Commands are idempotent by default; BDD scenarios spec the exceptions and the replies

- Status: Accepted
- Date: 2026-07-11
- Scope: command use cases of every service in the workspace (portal and game alike)

## Context

The feature files were growing mechanical scenarios of the shape "doing X twice is
idempotent" — `collections.feature` alone had two (save twice, remove the absent). Each
such scenario restates the same universal law with different nouns; at N commands that is
N copies of boilerplate a human must read past to find the behaviour that is actually
unique. The owner's call (2026-07-11): *"idempotent by default, otherwise dedicated BDD
scenario"* — a rule should be stated once, as law, not once per operation.

At the same time, two of those scenarios were never really about idempotence: they pin
the RESPONSE contract (`ALREADY_SAVED`, `NOT_SAVED`) that a UI needs to tell "added" from
"you already had it". That part is genuinely per-operation behaviour and belongs in the
spec.

An ADR alone enforces nothing. The no-null ADR (0001) works because a boundary layer
actually keeps null out; this one needs teeth of the same kind.

## Decision

1. **Every command use case is idempotent by default**: executing it twice leaves the
   system in exactly the state one execution leaves it in. Responses MAY differ between
   the first and second run (`SAVED` → `ALREADY_SAVED`) — idempotence is a property of
   state, not of replies (the PUT 201→200 precedent).
2. **The default is enforced by ONE generic test per service**, not by per-operation
   scenarios: run every command once and twice against fresh stores, compare the state
   fingerprints (`IdempotentCommandsTest` in user-collections is the reference
   implementation; memes' unique-constraint *claim* and comments' equivalents are the
   architectural mechanisms behind the same law).
3. **A dedicated BDD scenario is reserved for**: (a) a declared EXCEPTION — a command
   that cannot be idempotent must carry a scenario documenting its non-idempotent
   contract, visibly; (b) a RESPONSE contract richer than the law (the "already there"
   reply); (c) cross-service flows (the deletion saga), where the law spans processes.
4. **Scenario titles must not impersonate the law**: "…twice is idempotent" as a title is
   retired; a scenario that pins a reply is titled after the reply ("Saving twice tells
   the caller it was already there").

## Consequences

- Feature files describe behaviour that is unique to an operation; the universal law
  lives here once and in one generic test per service.
- New commands are covered by the law the moment they are added to the generic test's
  command list — forgetting them there is the new failure mode (mitigated by keeping the
  list next to the use-case wiring).
- Existing per-operation idempotence scenarios are retitled to what they actually pin
  (replies), not deleted — the reply contracts they carry are real spec.

## Enforcement registry (2026-07-11)

- `microservice-user-collections` — `IdempotentCommandsTest` (the reference); no exceptions.
- `microservice-comments` — `IdempotentCommandsTest`; declared exception: **AddComment**
  (two calls are two comments, by design — proven in the test).
- `microservice-memes` — `IdempotentCommandsTest` (delete / flag / purge under the law);
  declared exception: **CastVote** (the second identical vote TOGGLES the first away —
  a UX choice, proven in the test).
