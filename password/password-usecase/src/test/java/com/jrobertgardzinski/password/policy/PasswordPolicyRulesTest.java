package com.jrobertgardzinski.password.policy;

import com.jrobertgardzinski.password.config.*;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Password")
@Feature("Password policy")
@Story("Config")
class PasswordPolicyRulesTest {

    @Example
    @Label("withDefaults() produces DEFAULT CONFIGURATION")
    void withDefaultsProducesExpectedConfiguration() {
        Allure.parameter("DEFAULT CONFIGURATION", PasswordPolicy.withDefaults());

        PasswordPolicy policy = PasswordPolicy.withDefaults();

        assertThat(policy.minLength()).isEqualTo(MinLength.DEFAULT);
        assertThat(policy.specialChars()).isEqualTo(SpecialChars.DEFAULT);
        assertThat(policy.requiresUppercase()).isEqualTo(RequiresUppercase.DEFAULT);
        assertThat(policy.requiresLowercase()).isEqualTo(RequiresLowercase.DEFAULT);
        assertThat(policy.requiresDigit()).isEqualTo(RequiresDigit.DEFAULT);
    }

    @Example
    @Label("custom policy retains PROVIDED CONFIGURATION")
    void customPolicyRetainsProvidedConfiguration() {
        MinLength minLength = new MinLength(8);
        SpecialChars specialChars = new SpecialChars("$%");
        RequiresUppercase requiresUppercase = new RequiresUppercase(false);
        RequiresLowercase requiresLowercase = new RequiresLowercase(true);
        RequiresDigit requiresDigit = new RequiresDigit(false);

        Allure.parameter("minLength", minLength.value());
        Allure.parameter("specialChars", specialChars.value());
        Allure.parameter("requiresUppercase", requiresUppercase.value());
        Allure.parameter("requiresLowercase", requiresLowercase.value());
        Allure.parameter("requiresDigit", requiresDigit.value());

        PasswordPolicy policy = new PasswordPolicy(
                minLength, specialChars, requiresUppercase, requiresLowercase, requiresDigit);

        Allure.parameter("PROVIDED CONFIGURATION", policy);

        assertThat(policy.minLength()).isEqualTo(minLength);
        assertThat(policy.specialChars()).isEqualTo(specialChars);
        assertThat(policy.requiresUppercase()).isEqualTo(requiresUppercase);
        assertThat(policy.requiresLowercase()).isEqualTo(requiresLowercase);
        assertThat(policy.requiresDigit()).isEqualTo(requiresDigit);
    }
}
