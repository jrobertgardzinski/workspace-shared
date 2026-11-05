package com.jrobertgardzinski.security.domain.vo;

/**
 * Why a password did not prove an account. Named apart for the use cases; the wire must answer
 * both alike, or a guess learns which half it got wrong.
 */
public enum CredentialsError {
    /** No account goes by that address. */
    USER_NOT_FOUND,
    /** The account exists and the password is not its own. */
    WRONG_PASSWORD
}
