package com.jrobertgardzinski.config.ladder;

/**
 * Where a value is bound, named after what changing it costs. Declared from the highest rung
 * down: the level bound latest in the lifecycle wins. The label is what a report prints.
 */
public enum Level {
    /** A source the running system re-reads: a database row an administrator edits. */
    LIVE("live (database)"),
    /** Bound when the process starts: a property, an environment variable, a JVM flag. */
    RESTART("restart (properties/env)"),
    /** Bound when the artifact is built: the default in the code. Every ladder ends here. */
    REBUILD("rebuild (default)");

    private final String label;

    Level(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True when this level is bound before the process serves, so an illegal value fails the start. */
    public boolean boundBeforeServing() {
        return this != LIVE;
    }
}
