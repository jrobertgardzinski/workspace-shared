package com.jrobertgardzinski.hash.algorithm.argon2;

import io.qameta.allure.Story;

import java.util.OptionalInt;

@Story("Iterations")
class IterationsRulesTest extends BoundedIntRulesTest {

    @Override
    int min() {
        return Iterations.BOUNDARY;
    }

    @Override
    OptionalInt max() {
        return OptionalInt.empty();
    }

    @Override
    int create(int value) {
        return new Iterations(value).value();
    }
}
