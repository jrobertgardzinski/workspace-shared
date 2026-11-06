package com.jrobertgardzinski.hash.algorithm.argon2;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Hash Algorithm: Argon2")
@Feature("Configuration")
class Argon2ConfigRulesTest {

    @Example
    @Label("Builder without values produces defaults")
    void builderWithoutValuesProducesDefaults() {
        Allure.parameter("default", Argon2Config.withDefaults());

        Argon2Config config = Argon2Config.builder().build();

        assertThat(config.iterations().value()).isEqualTo(Iterations.DEFAULT);
        assertThat(config.memLimit().value()).isEqualTo(MemLimitInKB.DEFAULT);
        assertThat(config.parallelism().value()).isEqualTo(Parallelism.DEFAULT);
    }

}
