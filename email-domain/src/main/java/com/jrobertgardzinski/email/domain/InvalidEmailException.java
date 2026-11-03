package com.jrobertgardzinski.email.domain;

/**
 * A refused address, named by an {@link EmailError} rather than only by a sentence.
 *
 * The invariants below used to travel as prose ("Email domain must contain at least one '.': wp")
 * while every policy constraint travelled as a code ("RFC_FORMAT_INVALID"), and both ended up in
 * the SAME list on the wire — half of it readable only by a person, half only by a client. A
 * caller had no way to tell which entry it could branch on, and a UI could only print whatever it
 * was handed. The domain names its own failures now; the sentence stays as the exception message,
 * where a log reader wants it.
 *
 * It extends {@link IllegalArgumentException} on purpose: existing callers that catch the
 * unchecked argument failure keep working, and only those who ask for {@link #error()} see more.
 */
public final class InvalidEmailException extends IllegalArgumentException {

    // the same names as the enum, kept for callers that compare codes as strings
    public static final String EMAIL_BLANK = EmailError.EMAIL_BLANK.name();
    public static final String EMAIL_FORMAT_INVALID = EmailError.EMAIL_FORMAT_INVALID.name();
    public static final String LOCAL_PART_EMPTY = EmailError.LOCAL_PART_EMPTY.name();
    public static final String LOCAL_PART_DOT_AT_EDGE = EmailError.LOCAL_PART_DOT_AT_EDGE.name();
    public static final String LOCAL_PART_CONSECUTIVE_DOTS = EmailError.LOCAL_PART_CONSECUTIVE_DOTS.name();
    public static final String DOMAIN_EMPTY = EmailError.DOMAIN_EMPTY.name();
    public static final String DOMAIN_MISSING_DOT = EmailError.DOMAIN_MISSING_DOT.name();

    private final EmailError error;

    InvalidEmailException(EmailError error, String message) {
        super(message);
        this.error = error;
    }

    /** Which invariant the address broke — what a client branches on and a UI translates. */
    public EmailError error() {
        return error;
    }

    /** {@link #error()} by its stable name, for the wire and for callers that keep strings. */
    public String code() {
        return error.name();
    }
}
