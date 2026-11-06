package com.jrobertgardzinski.hash.algorithm.argon2;

public record Argon2Config(Iterations iterations, MemLimitInKB memLimit, Parallelism parallelism) {

    public static Argon2Config withDefaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int iterations = Iterations.DEFAULT;
        private int memLimit = MemLimitInKB.DEFAULT;
        private int parallelism = Parallelism.DEFAULT;

        public Builder iterations(int iterations) {
            this.iterations = iterations;
            return this;
        }

        public Builder memLimit(int memLimit) {
            this.memLimit = memLimit;
            return this;
        }

        public Builder parallelism(int parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public Argon2Config build() {
            return new Argon2Config(new Iterations(iterations), new MemLimitInKB(memLimit), new Parallelism(parallelism));
        }
    }
}
