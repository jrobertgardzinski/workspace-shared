package com.jrobertgardzinski.password.config;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Example;
import net.jqwik.api.Label;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Password")
@Feature("Password Security Configuration")
@Story("RequiresDigit")
class RequiresDigitRulesTest {
    @Example
    @Label("default is true")
    void defaultIsTrue() {
        assertThat(RequiresDigit.DEFAULT.value()).isTrue();
    }
}
