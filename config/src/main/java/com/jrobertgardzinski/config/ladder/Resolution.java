package com.jrobertgardzinski.config.ladder;

import java.util.List;

/**
 * What a {@link ConfigLadder} found on the way to its answer: the value, the label of the level
 * that supplied it, and every level above it that held a value the ladder refused, with the
 * reason. An empty {@code rejected} list is a clean climb.
 */
public record Resolution<T>(T value, String source, List<Rejected> rejected) {

    /**
     * A level that held something and was skipped: which level, what it held, and why it was
     * refused. The value is what the level held — the parsed value the gate refused, or the raw
     * text when it was not even the type — so the report can show what the hand wrote.
     */
    public record Rejected(String source, Object value, String reason) {
    }

    public Resolution {
        rejected = List.copyOf(rejected);
    }
}
