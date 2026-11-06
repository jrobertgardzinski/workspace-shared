package com.jrobertgardzinski.closure;

import com.jrobertgardzinski.identity.UserId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two questions every participant asks of a command before it acts, asked once here so that
 * three services cannot answer them three different ways.
 */
@Epic("Contract")
@Feature("Account closure vocabulary")
class ClosureCommandTest {

    private static final UserId LEAVER = UserId.random();

    private ClosureCommand command(UserId leaver, String initiatedBy) {
        return new ClosureCommand(ClosureMessages.PURGE_USER_CONTENT, "saga-1", leaver, initiatedBy,
                Optional.empty());
    }

    @Test
    @DisplayName("a command names somebody by id, or nobody")
    void an_unaddressed_command_is_recognised() {
        assertTrue(command(LEAVER, ClosureInitiator.SELF.wire()).isAddressed());
        assertFalse(command(null, ClosureInitiator.SELF.wire()).isAddressed());
    }

    @Test
    @DisplayName("the wire form of an id is read leniently: blank or mangled means absent")
    void the_wire_form_is_read_leniently() {
        assertEquals(Optional.of(LEAVER), ClosureCommand.userIdOf(LEAVER.toString()));
        assertEquals(Optional.empty(), ClosureCommand.userIdOf(null));
        assertEquals(Optional.empty(), ClosureCommand.userIdOf("  "));
        assertEquals(Optional.empty(), ClosureCommand.userIdOf("not-a-uuid"));
    }

    @Test
    @DisplayName("only an administrator's closure may carry conditions; every unknown word deletes")
    void conditions_need_an_administrator() {
        assertTrue(command(LEAVER, ClosureInitiator.ADMIN.wire()).allowsConditions());
        assertFalse(command(LEAVER, ClosureInitiator.SELF.wire()).allowsConditions());
        assertFalse(command(LEAVER, "MODERATOR").allowsConditions());
        assertFalse(command(LEAVER, null).allowsConditions());
    }
}
