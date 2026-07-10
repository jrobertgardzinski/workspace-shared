# ADR 0005: Two integration styles with microservice-email — on purpose

- Status: Accepted
- Date: 2026-07-10
- Scope: how services reach microservice-email (and the sms/push channels)

## Context

microservice-email is reached two different ways, which looks like an accident
until written down:

- **microservice-security → email: asynchronous** (transactional outbox →
  Kafka `mail-requests`). These mails are *consequences of state changes*
  (registration, reset, deletion saga): the mail must not be lost if the mail
  service is down, and the state change must not fail because of it. The
  outbox commits with the state change; delivery is at-least-once, deduplicated
  by the consumer.
- **microservice-paddock → email/sms/push: synchronous HTTP fan-out** (short
  timeout, API key, a blank URL disables a channel). These are *best-effort
  notifications*: an event reminder that arrives late or not at all costs
  nothing durable, and the paddock keeps no outbox state for them. Sync fan-out
  keeps the channel services identical (email is just one of three) and the
  failure mode local and observable.

## Decision

**Keep both, chosen by the message's obligation:**

- A mail the system **owes** the user after a committed state change goes
  through the **outbox/Kafka** path.
- A notification that is **best-effort by nature** may use the direct HTTP
  channel path.

New integrations pick by that rule, not by copying the nearest neighbour.

## Consequences

- The inconsistency is now a documented decision with an entry criterion,
  not an accident.
- If paddock ever gains notifications the system owes (e.g. billing), those
  move to an outbox — the rule, not the transport, is the invariant.
