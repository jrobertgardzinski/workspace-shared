package com.jrobertgardzinski.closure;

import com.jrobertgardzinski.unitofwork.UnitOfWork;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The contract for a participant that confirms through an outbox: the confirmation must be made inside the mark's unit of work. */
public abstract class AtomicParticipantContractTest extends ClosureParticipantContractTest {

    // Single-threaded on purpose, not thread-safe by omission: this whole fixture runs on the
    // JUnit test thread, synchronously, so `inside` only ever needs to answer "is confirm() being
    // called FROM WITHIN unitOfWork.run()'s step, right now, on this same call stack" — a call-
    // ordering question, not a concurrency one. UnitOfWork is the real port's promise of DATABASE
    // atomicity (the mark and the confirmation commit together or not at all); nothing here is
    // guarding against a second thread, because nothing here has one.
    private boolean inside;
    private int confirmedCount = -1;
    private final List<String> confirmedInside = new ArrayList<>();

    protected final UnitOfWork unitOfWork = step -> {
        inside = true;
        try {
            step.run();
        } finally {
            inside = false;
        }
    };

    protected final ClosureConfirmations confirmations = (sagaId, leaver, reserved) -> {
        if (inside) {
            confirmedInside.add(sagaId);
        }
        confirmedCount = reserved;
    };

    @Override
    protected final int confirmed() {
        return confirmedCount;
    }

    @Test
    @DisplayName("the confirmation is made inside the same unit of work as the mark")
    void the_confirmation_is_made_inside_the_unit_of_work() {
        givenLeaverHolds(3);
        handle(command(ClosureMessages.PURGE_USER_CONTENT));
        assertEquals(List.of(SAGA), confirmedInside);
    }
}
