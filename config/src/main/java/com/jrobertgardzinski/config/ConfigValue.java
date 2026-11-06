package com.jrobertgardzinski.config;

/**
 * The contract of a configuration value object: it knows the name it goes by on every level of a
 * configuration ladder, the value it holds, the value the code ships as the rebuild default, and
 * how to hold another value - through its own constructor, which is the gate: a value that is
 * not legal never exists. That is everything a ladder needs, so {@link Configuration} declares
 * one from the shipped instance alone: {@code configuration.liveOver(X.DEFAULT)}.
 *
 * <p>Java cannot make an interface demand a static member, so the convention that every
 * implementation also exposes {@code public static final X DEFAULT} (and {@code String KEY}) is
 * held by a law test in each library, not by the compiler.
 */
public interface ConfigValue<T> {

    /** The name on every level: the property, the database row, the report. */
    String key();

    T value();

    /** What the code ships: the rebuild level, the bottom of every ladder. */
    T defaultValue();

    /** The same rule holding this value - a new instance through the constructor, so the gate decides. */
    ConfigValue<T> holding(T value);
}
