package com.jrobertgardzinski.security.domain.vo;

/** What presenting a factor proof against a ticket found. */
public enum ProofStatus {
    /** The proof is right: the chain moved forward, or completed. */
    PASSED,
    /** No live chain stands behind the ticket: it is unknown, or it has aged past its window. */
    INVALID_TICKET,
    /** The proof is wrong and the ticket still has tries left. */
    WRONG_PROOF,
    /** The proof is wrong and it was the last try: the ticket is gone. */
    TOO_MANY_ATTEMPTS
}
