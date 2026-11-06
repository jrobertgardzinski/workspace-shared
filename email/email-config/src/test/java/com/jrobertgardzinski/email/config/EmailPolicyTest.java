package com.jrobertgardzinski.email.config;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("Email")
@Feature("Config")
@Story("EmailPolicy")
class EmailPolicyTest {

    private static final List<String> FIELD_NAMES = List.of("blockedDomains", "disposableDomains", "companyDomains");

    @Example
    @Label("default config → all domain wrappers are absent")
    void defaultConfig() {
        EmailPolicy config = new EmailPolicy();
        Allure.parameter("default", config);

        assertThat(config.blockedDomains()).isNull();
        assertThat(config.disposableDomains()).isNull();
        assertThat(config.companyDomains()).isNull();
    }
}
