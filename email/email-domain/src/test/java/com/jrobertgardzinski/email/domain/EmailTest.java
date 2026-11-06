package com.jrobertgardzinski.email.domain;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Email")
@Feature("Domain")
@Story("Email")
class EmailTest {

    @Property
    @Label("Invariant: rejects invalid input")
    void rejectsInvalidInput(@ForAll("invalidInputs") Tuple.Tuple2<String, String> tc) {
        Allure.parameter(tc.get1(), tc.get2());
        assertThrows(IllegalArgumentException.class, () -> Email.of(tc.get2()));
    }

    @Property
    @Label("Invariant: accepts valid addresses")
    void acceptsValidAddresses(@ForAll("validAddresses") Tuple.Tuple2<String, String> tc) {
        Allure.parameter(tc.get1(), tc.get2());
        assertThatCode(() -> Email.of(tc.get2())).doesNotThrowAnyException();
    }

    @Feature("Construction")
    @DisplayName("parses ")
    @ParameterizedTest(name = "\"{0}\" to \"{1}\"")
    @MethodSource("rawEmails")
    void parsesRawEmail(String raw, String expected) {
        assertThat(asRecordLikeString(Email.of(raw))).isEqualTo(expected);
    }

    static Stream<Arguments> rawEmails() {
        return Stream.of(
                Arguments.of("user@gmail.com",    asRecordLikeString(Email.of("user@gmail.com"))),
                Arguments.of("JohnDoe@GMAIL.COM", asRecordLikeString(Email.of("JohnDoe@GMAIL.COM")))
        );
    }

    static String asRecordLikeString(Email email) {
        return "Email{" +
                "local=" + email.local() +
                ", domain=" + email.domain() +
                '}';
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> invalidInputs() {
        return Arbitraries.of(
                Tuple.of("single space", " "),
                Tuple.of("missing @", "usergmail.com"),
                Tuple.of("multiple @", "user@@gmail.com"),
                Tuple.of("empty local", "@gmail.com"),
                Tuple.of("empty domain", "user@"),
                Tuple.of("domain without dot", "user@localhost")
        );
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, String>> validAddresses() {
        return Arbitraries.of(
                Tuple.of("simple", "user@gmail.com"),
                Tuple.of("with alias", "j.doe+spam@gmail.com"),
                Tuple.of("Polish TLD", "user@home.pl"),
                Tuple.of("compound TLD", "user@booking.co.uk")
        );
    }


}
