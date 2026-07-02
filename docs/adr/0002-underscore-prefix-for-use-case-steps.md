# ADR 0002: `_` prefix for package-private use-case steps

- Status: Accepted
- Date: 2026-07-02
- Scope: system layers of `microservice-security` (and any module that composes
  use cases out of internal steps)

## Context

A use case in the system layer (e.g. `Authentication`) is composed of several
smaller steps (`_BruteForceGuard`, `_VerifyCredentials`, `_GenerateSession`,
`_CleanBruteForceRecords`, `_UpdateBruteForceRecords`). The steps are
package-private: they are implementation details of the use case and must not
be used from outside the package.

Java's access modifiers already enforce that, but they are invisible in the
places where a reader actually scans a package: the file listing, imports, and
IDE navigation. There, a package-private step looks exactly like the public
API of the package.

## Decision

**Package-private classes that are internal steps of a use case carry a `_`
name prefix** (e.g. `_BruteForceGuard`). Public types of the package — the use
case itself, its result and factory — carry plain names.

This is a deliberate departure from standard Java naming conventions: the
prefix trades convention purity for an at-a-glance distinction between a
package's API and its internals.

## Consequences

- (+) The public surface of a use-case package is visible directly in the file
  listing — no need to open each class to check its modifier.
- (+) An accidental `public` on a step, or an import of a `_` class from
  another package, stands out immediately in review.
- (−) Non-standard naming: linters (Checkstyle `TypeName`) need an override,
  and newcomers will ask about it — this ADR is the answer.
- Test classes mirror the convention (`_BruteForceGuardTest`), keeping the
  step-to-test mapping obvious.
