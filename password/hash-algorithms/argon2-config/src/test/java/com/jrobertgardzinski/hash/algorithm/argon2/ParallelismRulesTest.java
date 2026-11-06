package com.jrobertgardzinski.hash.algorithm.argon2;

import io.qameta.allure.Story;

import java.util.OptionalInt;

@Story("Parallelism")
class ParallelismRulesTest extends BoundedIntRulesTest {

    @Override
    int min() {
        return Parallelism.MIN;
    }

    @Override
    OptionalInt max() {
        return OptionalInt.of(Parallelism.MAX);
    }

    @Override
    int create(int value) {
        return new Parallelism(value).value();
    }
}
