package com.jrobertgardzinski.password.domain;

/**
 * What judging a password against the policy found. A refusal says only that there was one: the
 * rules it broke are {@link PasswordError}s, because several can be broken at once. Sealed, not an
 * enum, so that a {@code switch} statement over it is exhaustive by the compiler.
 */
public sealed interface PasswordStatus {

    /** Every rule was met. */
    record Passed() implements PasswordStatus {}

    /** At least one rule was broken. */
    record Rejected() implements PasswordStatus {}
}
