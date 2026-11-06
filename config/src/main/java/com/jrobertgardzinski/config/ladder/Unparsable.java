package com.jrobertgardzinski.config.ladder;

/**
 * A rung held text that is not the type the key takes. Carries the raw text so the ladder can
 * report what the level held, not merely that it was refused.
 */
public final class Unparsable extends IllegalArgumentException {

    private final String raw;

    public Unparsable(String raw, IllegalArgumentException cause) {
        super("'" + raw + "' is not the type this key takes: " + cause.getMessage(), cause);
        this.raw = raw;
    }

    public String raw() {
        return raw;
    }
}
