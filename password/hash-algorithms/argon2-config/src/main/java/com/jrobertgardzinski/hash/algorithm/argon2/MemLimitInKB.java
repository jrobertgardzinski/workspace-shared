package com.jrobertgardzinski.hash.algorithm.argon2;

public record MemLimitInKB(int value) {

    public static final int MIN = 8;
    public static final int MAX = 4_194_304;
    public static final int DEFAULT = 65_536;

    public MemLimitInKB {
        if (value < MIN) throw new IllegalArgumentException("MemLimitInKB cannot be less than: " + MIN);
        if (value > MAX) throw new IllegalArgumentException("MemLimitInKB cannot be greater than: " + MAX);
    }
}
