package com.jrobertgardzinski.closure;

import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.unitofwork.UnitOfWork;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ClosureParticipant} that owes the orchestrator its own confirmation, and therefore has
 * the one step of this protocol whose two halves must not come apart: the mark and the word about
 * it are ONE unit of work. Hidden rows with no word owed is the failure mode — the saga waits for a
 * confirmation that will never come — and a word about rows that were never hidden is the other.
 *
 * <p>The axis whose confirmation is built by its consumer instead extends
 * {@link ClosureParticipant} directly and does its own marking; it has no outbox to write into and
 * so no transaction to share. That is the same split the contract tests make between
 * {@code ClosureParticipantContractTest} and {@code AtomicParticipantContractTest}.
 */
public abstract class AtomicClosureParticipant extends ClosureParticipant {

    private final ClosureConfirmations confirmations;
    private final UnitOfWork unitOfWork;

    protected AtomicClosureParticipant(ClosureConfirmations confirmations, UnitOfWork unitOfWork) {
        this.confirmations = confirmations;
        this.unitOfWork = unitOfWork;
    }

    @Override
    protected final int reserve(String sagaId, UserId leaver) {
        AtomicInteger reserved = new AtomicInteger();
        unitOfWork.run(() -> {
            int marked = mark(sagaId, leaver);
            confirmations.confirm(sagaId, leaver, marked);
            reserved.set(marked);
        });
        return reserved.get();
    }

    /** Hide this leaver's rows and answer how many — already inside the confirmation's unit of work. */
    protected abstract int mark(String sagaId, UserId leaver);

    /**
     * The unit of work the confirmation shares, for the steps that are not confirmed but are still
     * all-or-nothing: the closure, the compensation, and anything an axis publishes with them.
     */
    protected final void inUnitOfWork(Runnable step) {
        unitOfWork.run(step);
    }
}
