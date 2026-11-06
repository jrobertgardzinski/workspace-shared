package com.jrobertgardzinski.email.domain;

/**
 * What judging an address against its invariants and the e-mail policy found. A refusal says only
 * that there was one: what it broke are {@link EmailError}s, because several can be broken at once.
 */
public enum EmailStatus {
    /** Every invariant and rule was met. */
    PASSED,
    /** At least one invariant or rule was broken. */
    REJECTED
}
