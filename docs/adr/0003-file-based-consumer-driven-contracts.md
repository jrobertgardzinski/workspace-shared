# ADR 0003: File-based consumer-driven contracts (Pact without a broker)

- Status: Accepted
- Date: 2026-07-10
- Scope: every cross-service message and API this workspace's services exchange

## Context

The contracts between services were implicit. Producers built event JSON by
hand (`OutboxMailNotifiers`, `AccountDeletionOrchestrator`), consumers parsed
raw `JsonNode`s, and the consumers' tests used **hand-copied** payload strings.
A producer could rename a field a consumer reads and both repos' builds stayed
green; the break surfaced — at best — in the whole-stack smoke test
(`infra-smoke.sh`), the slowest and most expensive place to learn about it.
Whole-stack e2e as the only integration net does not scale with the number of
services (Sam Newman's classic objection).

The standard remedy is consumer-driven contract testing (Pact). The standard
deployment of it — a Pact broker — is infrastructure this one-person portfolio
does not need: every consumer and every producer is checked out side by side
in the workspace anyway.

## Decision

**Pact in file mode; the workspace layout replaces the broker.**

- Each consumer owns a pact test that drives its REAL consuming code
  (listener/consumer class) with the pact's payload, and declares **only the
  fields it actually reads** (tolerant reader — the producer may add fields
  freely). The generated pact is committed to the consumer repo's `pacts/`.
- The producer (`microservice-security`) owns provider tests that verify those
  pacts against its REAL producing code — the outbox notifiers and the saga
  orchestrator, never hand-written JSON — via `@PactFolder` pointing at the
  sibling checkout (`../../<consumer>/pacts`).
- When the sibling is not checked out (a truly standalone build), provider
  verification is **skipped, not failed** (`@EnabledIf` on the folder). The
  workspace reactor CI and microservice-security's own CI (which checks the
  consumer repos out) always run it.

Covered today: `mail-requests` (email ← security, six mail shapes) and
`content-commands` (memes/comments/user-collections ← security, the purge
command with and without a policy).

## Consequences

- A producer change that breaks a consumer's expectation goes red **in the
  producer's build**, with the consumer named in the failure — not in a live
  stack.
- Committed pact files are part of the consumer's API surface; regenerating
  them (`mvn test` writes to `pacts/`) and reviewing the diff is how a consumer
  announces new expectations.
- No broker to run, no versions/tags to manage; the trade-off is that
  verification is only as fresh as the sibling checkout. CI pins that: the
  producer's CI checks consumers out at `main`.
- Follow-ups tracked in `todo.md`: the confirmations direction
  (security ← participants), and the synchronous HTTP contracts (JWKS, `/me`).
