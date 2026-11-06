package com.jrobertgardzinski.closure;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one rule in this library, and it is a rule about which way to be wrong.
 */
@Epic("Account closure")
@Feature("Who asked")
class ClosureInitiatorTest {

    @Test
    @DisplayName("the two words read back as themselves, and travel as themselves")
    void the_words_round_trip() {
        assertEquals(ClosureInitiator.ADMIN, ClosureInitiator.of("ADMIN"));
        assertEquals(ClosureInitiator.SELF, ClosureInitiator.of("SELF"));
        assertEquals("ADMIN", ClosureInitiator.ADMIN.wire());
        assertEquals("SELF", ClosureInitiator.SELF.wire());
    }

    @Test
    @DisplayName("everything else is the owner: an unknown word must not buy an exception to erasure")
    void unknowns_fall_to_the_deleting_side() {
        // a message from before the field existed
        assertEquals(ClosureInitiator.SELF, ClosureInitiator.of(null));
        // a typo, a different case, a producer's idea of politeness
        assertEquals(ClosureInitiator.SELF, ClosureInitiator.of(""));
        assertEquals(ClosureInitiator.SELF, ClosureInitiator.of("admin"));
        assertEquals(ClosureInitiator.SELF, ClosureInitiator.of("Administrator"));
        assertEquals(ClosureInitiator.SELF, ClosureInitiator.of("moderator"));
    }

    @Test
    @DisplayName("conditions are an administrator's to state, and nobody else's")
    void only_an_administrator_may_attach_conditions() {
        assertTrue(ClosureInitiator.allowsConditions("ADMIN"));
        assertFalse(ClosureInitiator.allowsConditions("SELF"));
        assertFalse(ClosureInitiator.allowsConditions(null));
        assertFalse(ClosureInitiator.allowsConditions("admin"),
                "a near-miss must not license keeping somebody's content after they asked for it to go");
    }
}
