package com.jrobertgardzinski.closure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jrobertgardzinski.identity.UserId;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every participant owes the orchestrator, whatever it holds. A participant's own test
 * extends this and adds what is specific to its axis.
 */
public abstract class ClosureParticipantContractTest {

    /** The person leaving, by identity — the only key the closure has. */
    protected static final UserId LEAVER = UserId.of("0b7c1c2e-5d3a-4f1b-9e8d-6a5b4c3d2e1f");
    protected static final String SAGA = "7d9f9e2a-1f0a-4f6e-9a1b-2c3d4e5f6a7b";

    protected abstract void handle(ClosureCommand command);

    /** Makes the mark find this many rows of the leaver. */
    /** Rows of the leaver's — carrying {@link #LEAVER}, whatever address they were written under. */
    protected abstract void givenLeaverHolds(int rows);

    /** The count last confirmed to the orchestrator, or -1 when nothing was confirmed. */
    protected abstract int confirmed();

    protected abstract boolean nothingTouched();

    protected abstract boolean observedReservedNothing();

    protected static ClosureCommand command(String type, UserId leaver, String initiatedBy, String rule) {
        return new ClosureCommand(type, SAGA, leaver, initiatedBy, Optional.ofNullable(rule));
    }

    protected static ClosureCommand command(String type) {
        return command(type, LEAVER, ClosureInitiator.SELF.wire(), null);
    }

    @Test
    @DisplayName("the reversible step confirms the count it reserved")
    void mark_confirms_the_reserved_count() {
        givenLeaverHolds(40);
        handle(command(ClosureMessages.PURGE_USER_CONTENT));
        assertEquals(40, confirmed());
    }

    @Test
    @DisplayName("a mark that reserved nothing is still confirmed, and observed")
    void a_mark_that_reserved_nothing_is_confirmed_and_observed() {
        givenLeaverHolds(0);
        handle(command(ClosureMessages.PURGE_USER_CONTENT));
        assertEquals(0, confirmed());
        assertTrue(observedReservedNothing());
    }

    @Test
    @DisplayName("the closure and the compensation are not confirmed: the orchestrator is ending the case")
    void only_the_reversible_step_is_confirmed() {
        givenLeaverHolds(2);
        handle(command(ClosureMessages.ERASE_USER_CONTENT));
        handle(command(ClosureMessages.RESTORE_USER_CONTENT));
        assertEquals(-1, confirmed());
    }

    @Test
    @DisplayName("a command that names nobody is dropped without confirming")
    void an_unaddressed_command_is_dropped() {
        givenLeaverHolds(1);
        handle(command(ClosureMessages.PURGE_USER_CONTENT, null, ClosureInitiator.SELF.wire(), null));
        assertEquals(-1, confirmed());
        assertTrue(nothingTouched());
    }

    @Test
    @DisplayName("a command for somebody else's axis is ignored, not failed")
    void another_participants_command_is_ignored() {
        givenLeaverHolds(1);
        handle(command("SOMEBODY_ELSES_COMMAND"));
        assertEquals(-1, confirmed());
        assertTrue(nothingTouched());
    }
}
