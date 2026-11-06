package com.jrobertgardzinski.closure;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jrobertgardzinski.identity.UserId;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The field set three participants must not spell differently — pinned here so that adding one
 * is a single commit rather than three and a half.
 */
@Epic("Contract")
@Feature("Account closure vocabulary")
class ClosureConfirmationTest {

    private static final UserId LEAVER = UserId.of("0b7c1c2e-5d3a-4f1b-9e8d-6a5b4c3d2e1f");

    @Test
    @DisplayName("the whole message, and every participant sends the same one")
    void the_field_set_is_the_agreements() {
        assertEquals(Map.of(
                        "type", "USER_CONTENT_PURGED",
                        "userId", "0b7c1c2e-5d3a-4f1b-9e8d-6a5b4c3d2e1f",
                        "reserved", 40,
                        "version", 1,
                        "sagaId", "saga-1"),
                new ClosureConfirmation("saga-1", LEAVER, 40).fields());
    }

    @Test
    @DisplayName("a mark that reserved nothing still answers, and says so")
    void nothing_reserved_is_still_an_answer() {
        assertEquals(0, new ClosureConfirmation("saga-1", LEAVER, 0).fields().get("reserved"));
    }

    @Test
    @DisplayName("a blank saga id is LEFT OUT: absent falls back to the id, blank is thrown away")
    void a_blank_saga_id_is_omitted_rather_than_sent() {
        for (String nothing : new String[]{null, "", "   "}) {
            assertFalse(new ClosureConfirmation(nothing, LEAVER, 1).fields()
                            .containsKey("sagaId"),
                    "a blank saga id on the wire is a confirmation the orchestrator throws away");
        }
        assertEquals("saga-1",
                new ClosureConfirmation("saga-1", LEAVER, 1).fields().get("sagaId"));
    }
}
