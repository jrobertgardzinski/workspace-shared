package com.jrobertgardzinski.password.config;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Password")
@Feature("Password Security Configuration")
@Story("SpecialChars")
class SpecialCharsRulesTest {

    @Property(tries = 10)
    @Label("Invariant: accepts valid values")
    void acceptsValidValues(@ForAll("validValues") String value) {
        Allure.parameter("value", value);
        assertThat(new SpecialChars(value).value()).isEqualTo(value);
    }

    @Property
    @Label("Invariant: rejects invalid values")
    void rejectsInvalidValues(@ForAll("invalidValues") Tuple.Tuple2<String, String> example) {
        Allure.parameter(example.get1(), example.get2());
        assertThrows(IllegalArgumentException.class, () -> new SpecialChars(example.get2()));
    }

    @Property(tries = 20)
    @Label("Invariant: rejects duplicate characters")
    void rejectsDuplicateCharacters(@ForAll("valuesWithDuplicates") String value) {
        Allure.parameter("value", value);
        assertThrows(IllegalArgumentException.class, () -> new SpecialChars(value));
    }

    @Example
    @Label("Default value")
    void defaultValueIsDefinedAndAccepted() {
        Allure.parameter("default", SpecialChars.DEFAULT.value());
        assertThat(SpecialChars.DEFAULT.value()).isNotEmpty();
        assertThat(new SpecialChars(SpecialChars.DEFAULT.value()).value()).isEqualTo(SpecialChars.DEFAULT.value());
    }

    @Provide
    Arbitrary<String> validValues() {
        Arbitrary<String> uniqueCharStrings = Arbitraries.chars()
                .with(SpecialChars.ALLOWED.toCharArray())
                .set().ofMinSize(1).ofMaxSize(8)
                .map(set -> set.stream().map(String::valueOf).collect(Collectors.joining()));
        return Arbitraries.oneOf(
                Arbitraries.just(SpecialChars.DEFAULT.value()),
                uniqueCharStrings
        );
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> invalidValues() {
        return Arbitraries.of(
                Tuple.of("letter — not a special char", "a"),
                Tuple.of("digit — not a special char", "1")
        );
    }

    @Provide
    Arbitrary<String> valuesWithDuplicates() {
        return Arbitraries.chars()
                .with(SpecialChars.ALLOWED.toCharArray())
                .map(c -> String.valueOf(c).repeat(2));
    }
}
