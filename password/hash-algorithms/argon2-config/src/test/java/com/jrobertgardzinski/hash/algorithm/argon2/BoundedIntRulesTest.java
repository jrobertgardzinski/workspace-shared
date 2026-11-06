package com.jrobertgardzinski.hash.algorithm.argon2;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shared boundary rules for a value object wrapping a bounded {@code int}.
 * A subclass supplies its bounds and how to construct the value object; this
 * base drives the "accepts MIN/MAX, rejects MIN-1/MAX+1" invariants. When
 * {@link #max()} is empty the value object has no upper bound and the MAX cases
 * are skipped.
 */
@Epic("Hash Algorithm: Argon2")
@Feature("Configuration")
abstract class BoundedIntRulesTest {

    abstract int min();

    abstract OptionalInt max();

    /**
     * Constructs the value object and returns its raw value.
     * Throws {@link IllegalArgumentException} when the value is out of bounds.
     */
    abstract int create(int value);

    @Property
    @Label("Invariant: accepts valid values")
    void acceptsValidValues(@ForAll("validValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThat(create(value)).isEqualTo(value);
    }

    @Property
    @Label("Invariant: rejects invalid values")
    void rejectsInvalidValues(@ForAll("invalidValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        int value = boundary.get2();
        assertThrows(IllegalArgumentException.class, () -> create(value));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> validValues() {
        List<Tuple.Tuple2<String, Integer>> values = new ArrayList<>();
        values.add(Tuple.of("MIN", min()));
        max().ifPresent(max -> values.add(Tuple.of("MAX", max)));
        return Arbitraries.of(values);
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> invalidValues() {
        List<Tuple.Tuple2<String, Integer>> values = new ArrayList<>();
        values.add(Tuple.of("MIN - 1", min() - 1));
        max().ifPresent(max -> values.add(Tuple.of("MAX + 1", max + 1)));
        return Arbitraries.of(values);
    }
}
