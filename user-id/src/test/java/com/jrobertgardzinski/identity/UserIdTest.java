package com.jrobertgardzinski.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserIdTest {

    @Test
    void round_trips_through_its_wire_form() {
        UserId id = UserId.random();
        assertEquals(id, UserId.of(id.toString()));
        assertEquals(id, UserId.of("  " + id + "  "));
    }

    @Test
    void refuses_anything_that_is_not_a_uuid_without_echoing_it() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> UserId.of("alice@example.com"));
        assertEquals("not a user id: 17 chars", refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> UserId.of(null));
        assertThrows(IllegalArgumentException.class, () -> new UserId(null));
    }

    @Test
    void two_random_ids_differ() {
        assertNotEquals(UserId.random(), UserId.random());
        assertEquals(new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                UserId.of("00000000-0000-0000-0000-000000000001"));
    }
}
