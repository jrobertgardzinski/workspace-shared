# ADR 0004: Event envelopes carry a version; within a version, fields are only added

- Status: Accepted
- Date: 2026-07-10
- Scope: every event on the Kafka backbone (`mail-requests`, `content-commands`,
  `memes-events`, `comments-events`, `usercollections-events`)

## Context

Events had a `type` but no version. With one producer and eight consumer group
axes, "deploy both sides at once" was the implicit upgrade plan for any
incompatible change — which stops being a plan the moment two services deploy
independently (which is the point of separate repos and separate CI).

## Decision

**Every event envelope carries `"version": 1`, and two rules hold:**

1. **Within a version, evolution is additive only.** New fields may appear at
   any time; fields a consumer reads are never renamed, retyped or removed.
   Consumers are **tolerant readers**: they take the fields they need and
   ignore the rest (they already did — all consumers path-pick from JSON).
2. **A breaking change bumps the version**, and the producer emits the old
   shape alongside the new one (expand) until every consumer reads the new one
   (contract). Only then does the old shape retire.

The contract tests (ADR 0003) deliberately do NOT include `version` in the
consumers' pacts — no consumer branches on it today. It is the escape hatch
for tomorrow, cheap to carry (one field) and impossible to retrofit onto
events already in flight.

## Consequences

- Producers: `OutboxMailNotifiers`, `OutboxEmailCodeChannel`,
  `AccountDeletionOrchestrator` (security) and the purge-confirmation builders
  in memes/comments/user-collections all stamp `version: 1`.
- Consumers change nothing — extra fields were already ignored, which the pact
  verification now proves (the produced payloads include `version`; the pacts
  do not require it).
- A future consumer MAY branch on `version` when a v2 appears; until then any
  event without the field is implicitly v1 (the DLQ may hold pre-ADR events).
