package com.jrobertgardzinski.security.domain.vo;

/**
 * What checking a password against an account found. The two refusals are named apart for the use
 * cases; the wire must answer both alike, or a guess learns which half it got wrong.
 *
 * <p>Sealed rather than an enum so that a {@code switch} statement over it is exhaustive by the
 * compiler: a new outcome added here breaks every use case that reads it, instead of slipping
 * through the {@code Passed} arm into the success path.
 */
public sealed interface CredentialsStatus {

    /** The account exists and the password is its own. */
    record Passed() implements CredentialsStatus {}

    /** No account goes by that address. */
    record UserNotFound() implements CredentialsStatus {}

    /** The account exists and the password is not its own. */
    record WrongPassword() implements CredentialsStatus {}
}
