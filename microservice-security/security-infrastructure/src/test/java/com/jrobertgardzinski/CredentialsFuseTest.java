package com.jrobertgardzinski;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The fuses' refusal paths on a real {@code prod} boot (Flyway switched off so the refusal under
 * test is a fuse's, not a connection error's). The fuses themselves are absent under dev/test by
 * wiring — {@code @Requires(env = "prod")} — which every other test in this module proves by
 * booting.
 *
 * <p>Every fuse is {@code @Context} and the order they are built in is not fixed, so a case that
 * leaves a second setting out passes or fails on who gets asked first: the missing JWT pair passed
 * here and failed on the CI runner, where the metrics fuse went first. So each case starts from a
 * complete prod configuration and takes away exactly one thing.
 */
class CredentialsFuseTest {

    private static Map<String, Object> aCompleteProdConfiguration() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("kafka.enabled", false);
        configuration.put("flyway.datasources.default.enabled", false);
        // enough for the datasource BEAN to exist; nothing here opens a connection
        configuration.put("datasources.default.url", "jdbc:postgresql://localhost:1/none");
        configuration.put("datasources.default.username", "nobody");
        configuration.put("datasources.default.password", "not-a-dev-default");
        configuration.put("security.jwt.private-key", "not-checked-here");
        configuration.put("security.jwt.public-key", "not-checked-here");
        configuration.put("security.mfa.recovery.pepper", "a-real-pepper");
        configuration.put("security.mfa.secret-key", "ZGV2LWtleS1ub3QtYS1zZWNyZXQtMzJieXRlcyEhISE=");
        configuration.put("security.metrics.token", "a-scraper-token");
        return configuration;
    }

    private static Map<String, Object> aCompleteProdConfigurationWithout(String... settings) {
        Map<String, Object> configuration = aCompleteProdConfiguration();
        for (String setting : settings) {
            configuration.remove(setting);
        }
        return configuration;
    }

    private static Throwable aProdStartWith(Map<String, Object> configuration) {
        return catchThrowable(() -> ApplicationContext.builder()
                .deduceEnvironment(false)
                .environments("prod")
                .properties(configuration)
                .start());
    }

    @Test
    void a_missing_password_refuses_a_prod_start() {
        Throwable refusal = aProdStartWith(aCompleteProdConfigurationWithout("datasources.default.password"));

        assertThat(refusal).hasStackTraceContaining("requires an explicit datasource password");
    }

    @Test
    void a_known_dev_default_refuses_a_prod_start() {
        Map<String, Object> configuration = aCompleteProdConfiguration();
        configuration.put("datasources.default.password", "secret");

        Throwable refusal = aProdStartWith(configuration);

        assertThat(refusal).hasStackTraceContaining("known dev default");
    }

    @Test
    void a_missing_totp_key_refuses_a_prod_start() {
        Throwable refusal = aProdStartWith(aCompleteProdConfigurationWithout("security.mfa.secret-key"));

        assertThat(refusal)
                .as("a TOTP seed MINTS codes for ever and silently; without a key of its own a"
                        + " stolen table is a copy of every authenticator app in it")
                .hasStackTraceContaining("the TOTP seeds need their own key");
    }

    @Test
    void an_open_metrics_endpoint_refuses_a_prod_start() {
        Throwable refusal = aProdStartWith(aCompleteProdConfigurationWithout("security.metrics.token"));

        assertThat(refusal)
                .as("there is no separate management port, so /prometheus is served on the API's own"
                        + " connector: without a token it answers whoever can reach the service")
                .hasStackTraceContaining("the metrics endpoint needs a token");
    }

    @Test
    void a_missing_jwt_signing_pair_refuses_a_prod_start() {
        Throwable refusal = aProdStartWith(aCompleteProdConfigurationWithout(
                "security.jwt.private-key", "security.jwt.public-key"));

        assertThat(refusal)
                .as("with no pair configured the service would generate one at startup and serve a"
                        + " JWK set that changes on every restart and differs between replicas —"
                        + " offline verifiers would fail intermittently with nothing to read")
                .hasStackTraceContaining("requires an explicit JWT signing pair");
    }
}
