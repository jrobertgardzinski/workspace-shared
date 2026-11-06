package com.jrobertgardzinski.password.config;

import com.jrobertgardzinski.config.ConfigValue;

/**
 * Whether a password must contain a digit.
 */
public record RequiresDigit(Boolean value) implements ConfigValue<Boolean> {

    /** The name this rule goes by on every level of a deployment's configuration ladder. */
    public static final String KEY = "security.password.policy.requires.digit";

    public static final RequiresDigit DEFAULT = new RequiresDigit(true);

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Boolean defaultValue() {
        return DEFAULT.value();
    }

    @Override
    public RequiresDigit holding(Boolean value) {
        return new RequiresDigit(value);
    }
}
