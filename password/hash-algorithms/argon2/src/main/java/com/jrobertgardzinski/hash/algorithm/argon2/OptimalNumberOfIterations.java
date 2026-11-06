package com.jrobertgardzinski.hash.algorithm.argon2;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Helper;

/**
 * Developer utility — run main() to find the optimal iterations value for the current hardware.
 *
 * @see <a href="https://github.com/phxql/argon2-jvm?tab=readme-ov-file#recommended-parameters">argon2-jvm recommended parameters</a>
 */
public class OptimalNumberOfIterations {

    public static void main(String[] args) {
        long REQUIRED_CALCULATION_TIME = 1000;

        Argon2Config config = Argon2Config.withDefaults();
        Argon2 argon2 = Argon2Factory.create();
        int iterations = Argon2Helper.findIterations(argon2, REQUIRED_CALCULATION_TIME, config.memLimit().value(), config.parallelism().value());

        System.out.println("Set ITERATIONS for Argon2HashAlgorithm equal to: " + iterations);
    }
}
