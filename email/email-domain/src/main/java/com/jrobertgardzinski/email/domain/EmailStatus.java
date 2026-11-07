package com.jrobertgardzinski.email.domain;

/**
 * What judging an address against its invariants and the e-mail policy found. A refusal says only
 * that there was one: what it broke are {@link EmailError}s, because several can be broken at once.
 * Sealed, not an enum, so that a {@code switch} statement over it is exhaustive by the compiler.
 */
public sealed interface EmailStatus {

    /** Every invariant and rule was met. */
    record Passed() implements EmailStatus {}

    /** At least one invariant or rule was broken. */
    record Rejected() implements EmailStatus {}
}
