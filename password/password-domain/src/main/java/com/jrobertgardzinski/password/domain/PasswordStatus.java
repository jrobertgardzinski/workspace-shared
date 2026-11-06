package com.jrobertgardzinski.password.domain;

/**
 * What judging a password against the policy found. A refusal says only that there was one: the
 * rules it broke are {@link PasswordError}s, because several can be broken at once.
 */
public enum PasswordStatus {
    /** Every rule was met. */
    PASSED,
    /** At least one rule was broken. */
    REJECTED
}
