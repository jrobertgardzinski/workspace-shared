package com.jrobertgardzinski.email.domain;

/**
 * Every way an address can be refused, named once. The first seven are invariants of the value
 * itself ({@link Email#of} throws them inside {@link InvalidEmailException}); the last four are
 * policy rules a service applies to an address that did construct. A client branches on the
 * name, a UI translates it; the wire carries {@link #name()}.
 */
public enum EmailError {
    /** The whole address is missing. */
    EMAIL_BLANK,
    /** No single '@' with something on both sides of it. */
    EMAIL_FORMAT_INVALID,
    /** Nothing before the '@'. */
    LOCAL_PART_EMPTY,
    /** The part before the '@' starts or ends with a dot. */
    LOCAL_PART_DOT_AT_EDGE,
    /** The part before the '@' carries two dots in a row, which separates nothing. */
    LOCAL_PART_CONSECUTIVE_DOTS,
    /** Nothing after the '@'. */
    DOMAIN_EMPTY,
    /** The domain carries no dot, so it can name no registrable host. */
    DOMAIN_MISSING_DOT,

    /** The address does not match the RFC 5322 dot-atom shape. */
    RFC_FORMAT_INVALID,
    /** The domain is on the deployment's denylist. */
    DOMAIN_BLOCKED,
    /** The domain is a known throwaway-mailbox provider. */
    DISPOSABLE_DOMAIN,
    /** The deployment admits employees only, and this domain is not one of the company's. */
    NOT_A_COMPANY_DOMAIN
}
