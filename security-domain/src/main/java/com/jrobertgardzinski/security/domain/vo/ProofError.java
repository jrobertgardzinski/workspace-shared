package com.jrobertgardzinski.security.domain.vo;

/** Why a factor proof presented against a ticket did not move its chain forward. */
public enum ProofError {
    /** No live chain stands behind the ticket: it is unknown, or it has aged past its window. */
    INVALID_TICKET,
    /** The proof is wrong and the ticket still has tries left. */
    WRONG_PROOF,
    /** The proof is wrong and it was the last try: the ticket is gone. */
    TOO_MANY_ATTEMPTS
}
