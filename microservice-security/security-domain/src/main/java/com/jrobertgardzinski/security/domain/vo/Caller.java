package com.jrobertgardzinski.security.domain.vo;

import com.jrobertgardzinski.email.domain.Email;

/**
 * The person a live, accepted token names: the state of the CONVERSATION, as opposed to the state
 * of the system the repositories hold.
 *
 * <p>Not an address typed into a form. A use case that takes a {@code Caller} is talking to
 * somebody already authenticated, and that changes what a missing account means: for a stranger's
 * address it is a typo or a guess, and must answer exactly like a wrong password; for a caller it
 * means the account has gone from under a live session — deleted while the token still worked.
 * Three use cases used to read the same {@code Email} and only the controller knew which trust it
 * carried; the type now says so in the signature.
 */
public record Caller(Email email) {

    public Caller {
        if (email == null) {
            throw new IllegalArgumentException("a caller without an address is nobody");
        }
    }
}
