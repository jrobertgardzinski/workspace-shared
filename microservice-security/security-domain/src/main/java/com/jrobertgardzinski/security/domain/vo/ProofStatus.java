package com.jrobertgardzinski.security.domain.vo;

/**
 * What presenting a factor proof against a ticket found. Sealed, not an enum, so that a
 * {@code switch} statement over it is exhaustive by the compiler.
 */
public sealed interface ProofStatus {

    /** The proof is right: the chain moved forward, or completed. */
    record Passed() implements ProofStatus {}

    /** No live chain stands behind the ticket: it is unknown, or it has aged past its window. */
    record InvalidTicket() implements ProofStatus {}

    /** The proof is wrong and the ticket still has tries left. */
    record WrongProof() implements ProofStatus {}

    /** The proof is wrong and it was the last try: the ticket is gone. */
    record TooManyAttempts() implements ProofStatus {}
}
