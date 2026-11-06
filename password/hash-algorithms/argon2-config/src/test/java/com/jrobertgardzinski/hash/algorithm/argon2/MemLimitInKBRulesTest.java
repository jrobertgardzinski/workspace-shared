package com.jrobertgardzinski.hash.algorithm.argon2;

import io.qameta.allure.Story;

import java.util.OptionalInt;

@Story("MemLimitInKB")
class MemLimitInKBRulesTest extends BoundedIntRulesTest {

    @Override
    int min() {
        return MemLimitInKB.MIN;
    }

    @Override
    OptionalInt max() {
        return OptionalInt.of(MemLimitInKB.MAX);
    }

    @Override
    int create(int value) {
        return new MemLimitInKB(value).value();
    }
}
