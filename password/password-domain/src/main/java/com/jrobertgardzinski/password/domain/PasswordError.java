package com.jrobertgardzinski.password.domain;

/**
 * Every way a password can be refused, named once. The first is the value's own invariant
 * ({@link PlaintextPassword#of} refuses a blank); the rest are the rules of a password policy.
 * A client branches on the name, a UI translates it; the wire carries {@link #name()}.
 */
public enum PasswordError {
    /** Nothing was typed. */
    PASSWORD_BLANK,
    /** Shorter than the policy's minimum length. */
    MIN_LENGTH_NOT_MET,
    /** The policy wants a lowercase letter and found none. */
    LOWERCASE_REQUIRED,
    /** The policy wants an uppercase letter and found none. */
    UPPERCASE_REQUIRED,
    /** The policy wants a digit and found none. */
    DIGIT_REQUIRED,
    /** None of the policy's special characters is present. */
    SPECIAL_CHAR_REQUIRED
}
