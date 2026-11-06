package com.jrobertgardzinski.closure;

import com.jrobertgardzinski.identity.UserId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The skeleton held to the contract the three participants are held to — here, where it lives, and
 * not only in the repositories that extend it. Since this library stopped being words alone and
 * started carrying the guard, the switch and the mark-with-its-confirmation, "the consumers' tests
 * cover it" would mean the library's own behaviour is only ever tested by three copies of a
 * subclass, which is the arrangement it was extracted to end.
 *
 * <p>The participant below holds one number instead of a database. That is the whole point: what is
 * being tested is what happens AROUND an axis, so the axis is as small as it can be.
 */
@Epic("Architecture")
@Feature("Closure participant skeleton")
class SkeletonParticipantTest extends AtomicParticipantContractTest {

    /** An axis of exactly one number: how many rows this leaver has, and whether they are hidden. */
    private final class OneNumberParticipant extends AtomicClosureParticipant {

        private int rows;
        private int hidden;
        private boolean reservedNothing;

        private OneNumberParticipant() {
            super(confirmations, unitOfWork);
        }

        @Override
        protected int mark(String sagaId, UserId leaver) {
            hidden = rows;
            rows = 0;
            return hidden;
        }

        @Override
        protected ClosureOutcome erase(ClosureCommand command, UserId leaver) {
            int erased = hidden;
            hidden = 0;
            return new ClosureOutcome.Erased(erased, 0);
        }

        @Override
        protected ClosureOutcome restore(String sagaId, UserId leaver) {
            rows = hidden;
            hidden = 0;
            return new ClosureOutcome.Restored(rows);
        }

        @Override
        protected void reservedNothing() {
            reservedNothing = true;
        }
    }

    private final OneNumberParticipant participant = new OneNumberParticipant();

    @Override
    protected void handle(ClosureCommand command) {
        participant.handle(command);
    }

    @Override
    protected void givenLeaverHolds(int rows) {
        participant.rows = rows;
    }

    @Override
    protected boolean nothingTouched() {
        return participant.hidden == 0;
    }

    @Override
    protected boolean observedReservedNothing() {
        return participant.reservedNothing;
    }

    @Test
    @DisplayName("a command of somebody else's axis is named back, not acted on")
    void a_foreign_command_is_named_back() {
        givenLeaverHolds(3);

        // the saga's topic carries every participant's commands, so this is the ordinary case and
        // not an error — and the outcome says WHICH command, because a consumer that routes by it
        // has nothing else to go on
        assertEquals(new ClosureOutcome.NotOurs("SOMEBODY_ELSES_COMMAND"),
                participant.handle(command("SOMEBODY_ELSES_COMMAND")));
    }

    @Test
    @DisplayName("a command that names nobody is dropped as unaddressed, whatever it asked for")
    void an_unaddressed_command_is_named_back() {
        givenLeaverHolds(3);

        assertEquals(new ClosureOutcome.Unaddressed(ClosureMessages.ERASE_USER_CONTENT),
                participant.handle(command(ClosureMessages.ERASE_USER_CONTENT, null,
                        ClosureInitiator.SELF.wire(), null)));
    }

    @Test
    @DisplayName("each step answers with its own outcome, and only the mark carries a confirmation")
    void each_step_has_its_own_outcome() {
        givenLeaverHolds(2);

        assertEquals(new ClosureOutcome.Reserved(2),
                participant.handle(command(ClosureMessages.PURGE_USER_CONTENT)));
        assertEquals(new ClosureOutcome.Restored(2),
                participant.handle(command(ClosureMessages.RESTORE_USER_CONTENT)));
        assertEquals(new ClosureOutcome.Reserved(2),
                participant.handle(command(ClosureMessages.PURGE_USER_CONTENT)));
        assertEquals(new ClosureOutcome.Erased(2, 0),
                participant.handle(command(ClosureMessages.ERASE_USER_CONTENT)));
    }
}
