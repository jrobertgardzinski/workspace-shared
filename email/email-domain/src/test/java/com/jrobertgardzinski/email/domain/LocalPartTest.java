package com.jrobertgardzinski.email.domain;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Email")
@Feature("Domain")
@Story("LocalPart")
class LocalPartTest {

    @Property
    @Label("Invariant: rejects invalid input")
    void rejectsInvalidInput(@ForAll("invalidInputs") Tuple.Tuple2<String, String> tc) {
        Allure.parameter(tc.get1(), tc.get2());
        assertThrows(IllegalArgumentException.class, () -> LocalPart.of(tc.get2()));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> invalidInputs() {
        return Arbitraries.of(
                Tuple.of("leading dot", ".john"),
                Tuple.of("trailing dot", "john."),
                Tuple.of("two dots in a row", "a..b"),
                Tuple.of("three dots in a row", "a...b"),
                Tuple.of("dots in a row further along", "john..doe.smith")
        );
    }

    @Property
    @Label("Invariant: accepts valid values")
    void acceptsValidValues(@ForAll("validValues") Tuple.Tuple2<String, String> tc) {
        Allure.parameter(tc.get1(), tc.get2());
        assertThatCode(() -> LocalPart.of(tc.get2())).doesNotThrowAnyException();
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> validValues() {
        return Arbitraries.of(
                Tuple.of("plain", "user"),
                Tuple.of("with dot", "j.doe"),
                Tuple.of("with alias", "j.doe+spam"),
                Tuple.of("alphanumeric", "user123")
        );
    }

    record Refusal(String input, String code) {}

    /**
     * The CODE is what a client branches on and a UI translates, so it is contract, not detail.
     * These are the same refusals as above, read by their name rather than by their sentence.
     */
    @Property
    @Label("Invariant: a refusal names itself with a code")
    void refusalNamesItself(@ForAll("refusals") Refusal refusal) {
        Allure.parameter(refusal.input, refusal.code);
        InvalidEmailException thrown =
                assertThrows(InvalidEmailException.class, () -> LocalPart.of(refusal.input));
        assertThat(thrown.code()).isEqualTo(refusal.code);
    }

    @Provide
    Arbitrary<Refusal> refusals() {
        return Arbitraries.of(
                new Refusal("", InvalidEmailException.LOCAL_PART_EMPTY),
                new Refusal(".john", InvalidEmailException.LOCAL_PART_DOT_AT_EDGE),
                new Refusal("john.", InvalidEmailException.LOCAL_PART_DOT_AT_EDGE),
                new Refusal("a..b", InvalidEmailException.LOCAL_PART_CONSECUTIVE_DOTS)
        );
    }

    record Normalization(String entered, String expected) {}

    @Label("Gmail normalization")
    @Property
    void normalizesGmail(@ForAll("gmailNormalization") Normalization normalization) {
        Allure.parameter("normalizes", String.format("%s -> %s", normalization.entered, normalization.expected));
        assertThat(LocalPart.of(normalization.entered).normalize("gmail.com").value()).isEqualTo(normalization.expected);
    }
    @Provide
    Arbitrary<Normalization> gmailNormalization() {
        return Arbitraries.of(
                new Normalization("J.Doe+spam", "jdoe"),
                new Normalization("j.d.o.e", "jdoe"),
                new Normalization("JDOE+work", "jdoe")
        );
    }

    @Label("Yahoo normalization")
    @Property
    void normalizesYahoo(@ForAll("yahooNormalization") Normalization normalization) {
        Allure.parameter("normalizes", String.format("%s -> %s", normalization.entered, normalization.expected));
        assertThat(LocalPart.of(normalization.entered).normalize("yahoo.com").value()).isEqualTo(normalization.expected);
    }
    @Provide
    Arbitrary<Normalization> yahooNormalization() {
        return Arbitraries.of(
                new Normalization("J.Doe-lists", "j.doe"),
                new Normalization("USER", "user")
        );
    }

    @Label("Outlook normalization")
    @Property
    void normalizesOutlook(@ForAll("outlookNormalization") Normalization normalization) {
        Allure.parameter("normalizes", String.format("%s -> %s", normalization.entered, normalization.expected));
        assertThat(LocalPart.of(normalization.entered).normalize("outlook.com").value()).isEqualTo(normalization.expected);
    }
    @Provide
    Arbitrary<Normalization> outlookNormalization() {
        return Arbitraries.of(
                new Normalization("J.Doe+alias", "j.doe"),
                new Normalization("J.Doe", "j.doe")
        );
    }

    @Label("iCloud normalization")
    @Property
    void normalizesICloud(@ForAll("iCloudNormalization") Normalization normalization) {
        Allure.parameter("normalizes", String.format("%s -> %s", normalization.entered, normalization.expected));
        assertThat(LocalPart.of(normalization.entered).normalize("icloud.com").value()).isEqualTo(normalization.expected);
    }
    @Provide
    Arbitrary<Normalization> iCloudNormalization() {
        return Arbitraries.of(
                new Normalization("J.Doe", "j.doe"),
                new Normalization("USER", "user")
        );
    }

    @Label("Every other domain: case is not part of the identity either")
    @Property
    void normalizesAnyOtherDomain(@ForAll("ordinaryDomainNormalization") Normalization normalization) {
        Allure.parameter("normalizes", String.format("%s -> %s", normalization.entered, normalization.expected));
        assertThat(LocalPart.of(normalization.entered).normalize("corp.com").value())
                .isEqualTo(normalization.expected);
    }
    @Provide
    Arbitrary<Normalization> ordinaryDomainNormalization() {
        // the local part is lower-cased for EVERY domain (2026-09-12). It used to be left alone
        // outside the four big providers, so Alice@corp.com and alice@corp.com were two accounts
        // and one person's sign-in answered "wrong password" to their own address in capitals.
        return Arbitraries.of(
                new Normalization("Alice", "alice"),
                new Normalization("ALICE", "alice"),
                new Normalization("alice", "alice"),
                // and ONLY the case: a plus or a dot means something different on a corporate
                // server than it does at gmail, and merging them would merge two people
                new Normalization("A.Lice+team", "a.lice+team"),
                new Normalization("First.Last-x", "first.last-x")
        );
    }
}
