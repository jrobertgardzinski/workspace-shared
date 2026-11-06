package com.jrobertgardzinski.hash.algorithm.argon2;

public record Parallelism(int value) {

    public static final int MIN = 1;
    public static final int MAX = 16;
    public static final int DEFAULT = 1;

    public Parallelism {
        if (value < MIN) throw new IllegalArgumentException("Parallelism cannot be less than: " + MIN);
        if (value > MAX) throw new IllegalArgumentException("Parallelism cannot be greater than: " + MAX);
    }
}
