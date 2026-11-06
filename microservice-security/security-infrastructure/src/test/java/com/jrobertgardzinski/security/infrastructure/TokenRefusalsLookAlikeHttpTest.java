package com.jrobertgardzinski.security.infrastructure;

import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
import com.jrobertgardzinski.CapturingPasswordResetNotifier;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The use cases behind a mailed link name each refusal for what it is: the token is unknown, or it
 * is too old. The WIRE must not. Telling the two apart would tell a stranger holding a guessed or
 * leaked link that something was once started for that address, so the edge folds them into one
 * answer, and this test is what keeps a future controller from unfolding them.
 */
@Epic("Anti-enumeration")
@Feature("Refusals of a mailed link look alike")
class TokenRefusalsLookAlikeHttpTest {

    private static final String PASSWORD = "StrongPassword1!";
    private static final String NEW_PASSWORD = "BrandNewPassword1!";

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class, "test");
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL()).toBlocking();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("an expired reset link is answered exactly like an unknown one")
    void an_expired_reset_link_looks_unknown() {
        String owner = "expired-reset@example.com";
        register(owner);
        exchange(HttpRequest.POST("/verify-email", Map.of("token", verificationMails().lastTokenFor(owner))));
        exchange(HttpRequest.POST("/reset-password/request", Map.of("email", owner)));
        String token = resetMails().lastTokenFor(owner);
        assertNotNull(token, "no reset link was e-mailed");

        // the reset window is 60 minutes
        advanceTheClock("PT61M");

        HttpResponse<Map> expired = exchange(HttpRequest.POST("/reset-password",
                Map.of("token", token, "password", NEW_PASSWORD)));
        HttpResponse<Map> unknown = exchange(HttpRequest.POST("/reset-password",
                Map.of("token", "a-token-nobody-was-ever-mailed", "password", NEW_PASSWORD)));

        assertEquals(HttpStatus.BAD_REQUEST, expired.getStatus());
        assertEquals(unknown.getStatus(), expired.getStatus());
        assertEquals(unknown.getBody(Map.class), expired.getBody(Map.class),
                "an expired link answering differently confirms the address had an account");
    }

    @Test
    @DisplayName("an expired verification link is answered exactly like an unknown one")
    void an_expired_verification_link_looks_unknown() {
        String owner = "expired-verification@example.com";
        register(owner);
        String token = verificationMails().lastTokenFor(owner);
        assertNotNull(token, "no verification link was e-mailed");

        // the verification window is 48 hours
        advanceTheClock("PT49H");

        HttpResponse<Map> expired = exchange(HttpRequest.POST("/verify-email", Map.of("token", token)));
        HttpResponse<Map> unknown = exchange(HttpRequest.POST("/verify-email",
                Map.of("token", "a-token-nobody-was-ever-mailed")));

        assertEquals(HttpStatus.BAD_REQUEST, expired.getStatus());
        assertEquals(unknown.getStatus(), expired.getStatus());
        assertEquals(unknown.getBody(Map.class), expired.getBody(Map.class),
                "an expired link answering differently says a verification was once started here");
    }

    private void register(String email) {
        assertEquals(HttpStatus.CREATED,
                exchange(HttpRequest.POST("/register", Map.of("email", email, "password", PASSWORD))).getStatus());
    }

    private void advanceTheClock(String duration) {
        assertEquals(HttpStatus.OK,
                exchange(HttpRequest.POST("/test/clock/advance", Map.of("duration", duration))).getStatus(),
                "the steerable clock did not move");
    }

    private CapturingEmailVerificationNotifier verificationMails() {
        return server.getApplicationContext().getBean(CapturingEmailVerificationNotifier.class);
    }

    private CapturingPasswordResetNotifier resetMails() {
        return server.getApplicationContext().getBean(CapturingPasswordResetNotifier.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private HttpResponse<Map> exchange(HttpRequest<?> request) {
        try {
            return client.exchange(request, Map.class);
        } catch (HttpClientResponseException refused) {
            return (HttpResponse<Map>) refused.getResponse();
        }
    }
}
