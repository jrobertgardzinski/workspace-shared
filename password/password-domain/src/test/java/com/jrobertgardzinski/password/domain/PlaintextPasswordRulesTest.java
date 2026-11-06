package com.jrobertgardzinski.password.domain;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Epic("Password")
@Feature("Domain")
@Story("PlaintextPassword")
class PlaintextPasswordRulesTest {

    @Property(tries = 3)
    @Label("Security: toString() does not reveal plaintext")
    void toStringDoesNotRevealPlaintext(@ForAll @StringLength(min = 3) @AlphaChars String value) {
        PlaintextPassword password = PlaintextPassword.of(value);
        Allure.parameter("password", value);
        Allure.parameter("toString()", password.toString());
        assertThat(password.toString()).contains(PlaintextPassword.REDACTED);
    }
}
