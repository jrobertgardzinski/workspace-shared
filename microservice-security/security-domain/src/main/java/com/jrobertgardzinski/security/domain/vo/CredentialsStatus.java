package com.jrobertgardzinski.security.domain.vo;

/**
 * What checking a password against an account found. The two refusals are named apart for the use
 * cases; the wire must answer both alike, or a guess learns which half it got wrong.
 */
public enum CredentialsStatus {
    /** The account exists and the password is its own. */
    PASSED,
    /** No account goes by that address. */
    USER_NOT_FOUND,
    /** The account exists and the password is not its own. */
    WRONG_PASSWORD
}
