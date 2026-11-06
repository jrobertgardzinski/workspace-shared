package com.jrobertgardzinski.config;

import com.jrobertgardzinski.config.ladder.Resolution;

/**
 * A key declared live: one the running system may be told a value under, and answers about, without
 * a restart. The declaration ({@code configuration.liveOver(X.DEFAULT)}) is the only place a key
 * joins this catalogue, so a generic write endpoint gets every live key for free and cannot be
 * told a key nobody reads.
 */
public interface LiveKey {

    String key();

    /**
     * The rule holding this text: parsed by the rule's type and gated by its constructor, exactly
     * as the ladder would read the same text from a row. Text that is not the type, or a value
     * the rule refuses, is an {@link IllegalArgumentException} with the reason - and nothing is
     * held.
     */
    ConfigValue<?> holding(String text);

    /** What is in force under the key right now, with its provenance. */
    Resolution<? extends ConfigValue<?>> resolution();
}
