package com.jrobertgardzinski.password.config;

import com.jrobertgardzinski.config.ConfigValue;

/**
 * The fewest characters a password may have; never below {@value #BOUNDARY}.
 */
public record MinLength(Integer value) implements ConfigValue<Integer> {

    /** The name this rule goes by on every level of a deployment's configuration ladder. */
    public static final String KEY = "security.password.policy.min.length";

    public static final int BOUNDARY = 5;
    public static final MinLength DEFAULT = new MinLength(BOUNDARY);

    public MinLength {
        if (value < BOUNDARY)
            throw new IllegalArgumentException("minLength must be at least " + BOUNDARY);
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Integer defaultValue() {
        return DEFAULT.value();
    }

    @Override
    public MinLength holding(Integer value) {
        return new MinLength(value);
    }
}
