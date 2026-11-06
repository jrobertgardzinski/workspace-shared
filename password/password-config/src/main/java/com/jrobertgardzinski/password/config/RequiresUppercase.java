package com.jrobertgardzinski.password.config;

import com.jrobertgardzinski.config.ConfigValue;

/**
 * Whether a password must contain an uppercase letter.
 */
public record RequiresUppercase(Boolean value) implements ConfigValue<Boolean> {

    /** The name this rule goes by on every level of a deployment's configuration ladder. */
    public static final String KEY = "security.password.policy.requires.uppercase";

    public static final RequiresUppercase DEFAULT = new RequiresUppercase(true);

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Boolean defaultValue() {
        return DEFAULT.value();
    }

    @Override
    public RequiresUppercase holding(Boolean value) {
        return new RequiresUppercase(value);
    }
}
