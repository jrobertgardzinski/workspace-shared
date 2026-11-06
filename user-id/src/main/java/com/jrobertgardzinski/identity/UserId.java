package com.jrobertgardzinski.identity;

import java.util.UUID;

/** The stable identity of a person: the UUID security minted at registration. Never reused. */
public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("a user id needs a value");
        }
    }

    public static UserId random() {
        return new UserId(UUID.randomUUID());
    }

    /** The wire form back into the type; anything that is not a UUID is refused. */
    public static UserId of(String raw) {
        try {
            return new UserId(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new IllegalArgumentException("not a user id: " + (raw == null ? "null" : raw.length() + " chars"));
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
