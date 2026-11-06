package com.jrobertgardzinski.password.config;

import com.jrobertgardzinski.config.ConfigValue;

/**
 * The special characters a password may use: a non-empty subset of {@link #ALLOWED}, without repeats.
 */
public record SpecialChars(String value) implements ConfigValue<String> {

    /** The name this rule goes by on every level of a deployment's configuration ladder. */
    public static final String KEY = "security.password.policy.special.chars";

    public static final String ALLOWED = "!\"#$%&'()*+,-./:;<=>?@[\\]^_{|}~";
    public static final SpecialChars DEFAULT = new SpecialChars("!@#$%^&*");

    public SpecialChars {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("specialChars must not be empty");
        for (char c : value.toCharArray()) {
            if (ALLOWED.indexOf(c) < 0)
                throw new IllegalArgumentException("character not in allowed set: '" + c + "'");
        }
        if (value.chars().distinct().count() != value.length())
            throw new IllegalArgumentException("specialChars must not contain duplicate characters");
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String defaultValue() {
        return DEFAULT.value();
    }

    @Override
    public SpecialChars holding(String value) {
        return new SpecialChars(value);
    }
}
