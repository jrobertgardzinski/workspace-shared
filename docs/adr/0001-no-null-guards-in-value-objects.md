# ADR 0001: No null guards in Value Objects

- Status: Accepted
- Date: 2026-07-01
- Scope: domain layers of `email`, `password`, `microservice-security`

## Context

Input reaches the domain only through the application layer, which validates
and maps raw input (e.g. `String`) into domain types at its boundary. If that
boundary works correctly, `null` cannot reach a Value Object.

The question was whether every VO should still defend itself against `null`
(e.g. `Objects.requireNonNull` in every factory and constructor, covered by a
dedicated null-safety test rendered in Allure). That guarantees the invariant on
paper, at the cost of boilerplate in every VO and constructor — including
package-private constructors that only ever receive already-validated VOs.

## Decision

**No `Objects.requireNonNull()` (or equivalent null guards) in Value Objects.**

Preventing `null` is the responsibility of the application layer, not the domain
layer. Domain VOs assume their inputs are non-null.

Format/emptiness validation stays in the domain (a VO still rejects a malformed
or blank value with `IllegalArgumentException`) — this ADR is only about `null`
guards, not about domain invariants in general.

## Consequences

- (+) Less boilerplate; cleaner VO constructors and factories.
- (+) The domain reads as intended: it expresses business rules, not defensive
  plumbing.
- (−) The domain is sensitive to `null`: if boundary validation is wrong, the
  failure surfaces as an `NPE` inside the domain.
- This is acceptable: such an NPE is a **programming error** (a bug in the
  application layer), not a business error. It should be caught by application-
  layer tests, not defended against in every VO.

## Review trigger

Revisit this decision if either holds:

- an external system starts pushing data **directly** into the domain, bypassing
  the application-layer boundary; or
- null-related NPEs actually surface in practice rather than being caught by
  application-layer tests.

Note: the auth service is growing from 5 use cases (Register, Authenticate,
RefreshSession, Authorize, Logout) towards ~15 as the backlog lands. Growth
alone is not a trigger — the boundary discipline is what matters — but it is a
reason to keep application-layer boundary tests thorough.
