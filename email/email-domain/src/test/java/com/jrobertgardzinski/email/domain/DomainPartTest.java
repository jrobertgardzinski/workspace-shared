package com.jrobertgardzinski.email.domain;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Email")
@Feature("Domain")
@Story("DomainPart")
class DomainPartTest {

    @Property
    @Label("Invariant: rejects invalid input")
    void rejectsInvalidInput(@ForAll("invalidInputs") Tuple.Tuple2<String, String> tc) {
        Allure.parameter(tc.get1(), tc.get2());
        assertThrows(IllegalArgumentException.class, () -> DomainPart.of(tc.get2()));
    }

    @Property
    @Label("Invariant: accepts valid values")
    void acceptsValidValues(@ForAll("validValues") Tuple.Tuple2<String, String> tc) {
        Allure.parameter(tc.get1(), tc.get2());
        assertThatCode(() -> DomainPart.of(tc.get2())).doesNotThrowAnyException();
    }

    @Feature("Domain normalization")
    @Property
    void normalizesToLowercaseByDefault(@ForAll("toNormalized") Normalization normalization) {
        Allure.parameter("normalizes", String.format("%s -> %s", normalization.entered, normalization.expected));
        assertThat(DomainPart.of(normalization.entered()).value()).isEqualTo(normalization.expected());
    }

    record Normalization(String entered, String expected) {}

    @Provide
    Arbitrary<Normalization> toNormalized() {
        return Arbitraries.of(
                new Normalization("GMAIL.COM", "gmail.com"),
                new Normalization("Gmail.Com", "gmail.com"),
                new Normalization("googlemail.com", "googlemail.com"),
                new Normalization("HOME.PL", "home.pl"),
                new Normalization("Booking.Co.Uk", "booking.co.uk")
        );
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> invalidInputs() {
        return Arbitraries.of(
                Tuple.of("no dot", "localhost")
        );
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> validValues() {
        return Arbitraries.of(
                Tuple.of("simple", "gmail.com"),
                Tuple.of("Polish TLD", "home.pl"),
                Tuple.of("subdomain", "mail.google.com"),
                Tuple.of("compound TLD", "booking.co.uk")
        );
    }
}
