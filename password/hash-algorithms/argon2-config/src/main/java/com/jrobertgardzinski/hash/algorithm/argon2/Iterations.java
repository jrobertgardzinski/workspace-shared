package com.jrobertgardzinski.hash.algorithm.argon2;

public record Iterations(int value) {

    public static final int BOUNDARY = 1;
    public static final int DEFAULT = 3;

    public Iterations {
        if (value < 1) throw new IllegalArgumentException("Iterations cannot be less than: " + BOUNDARY);
    }
}
