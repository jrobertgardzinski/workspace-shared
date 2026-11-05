package com.jrobertgardzinski.security.infrastructure.feature.account;

import com.jrobertgardzinski.CapturingEmailVerificationNotifier;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * HTTP glue for {@code change-password.feature}. Black-box: authenticate for an access token, change
 * the password, then show the new password authenticates and the old one does not.
 */
public class HttpChangePasswordSteps {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    private String email;
    private String accessToken;
    private HttpResponse<Map> changeResponse;

    @Before
    public void startServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server.getApplicationContext()
                .createBean(HttpClient.class, server.getURL())
                .toBlocking();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Given("a registered USER {string} with password {string}")
    public void aRegisteredUser(String email, String password) {
        this.email = email;
        HttpResponse<Map> seeded = exchange(HttpRequest.POST("/register", Map.of("email", email, "password", password)));
        assertEquals(HttpStatus.CREATED, seeded.getStatus(), "failed to seed the user");
        verifySeededUser(email);
    }

    @Given("the USER has AUTHENTICATED")
    public void theUserHasAuthenticated() {
        accessToken = accessTokenFor("OldPassword1!");
    }

    @When("the USER CHANGES the password from {string} to {string}")
    public void theUserChangesThePassword(String currentPassword, String newPassword) {
        changeResponse = exchange(HttpRequest.POST("/account/password",
                        Map.of("currentPassword", currentPassword, "newPassword", newPassword))
                .header("Authorization", "Bearer " + accessToken));
    }

    @Then("the USER can AUTHENTICATE with {string}")
    public void canAuthenticateWith(String password) {
        assertEquals(HttpStatus.OK, authenticate(password).getStatus());
    }

    @Then("the USER cannot AUTHENTICATE with {string}")
    public void cannotAuthenticateWith(String password) {
        assertEquals(HttpStatus.UNAUTHORIZED, authenticate(password).getStatus());
    }

    @Then("the password CHANGE is rejected")
    public void thePasswordChangeIsRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, changeResponse.getStatus());
        // one answer whether the account is gone or the password is wrong: the wire never says which
        assertEquals("FAILED_AUTHENTICATION_ATTEMPT", changeResponse.getBody(Map.class).orElseThrow().get("status"));
    }

    @Then("the new password is flagged as invalid")
    public void theNewPasswordIsFlaggedAsInvalid() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, changeResponse.getStatus());
        // the shape /register answers with: one {CODE: parameter in force} per broken rule
        List<?> passwordErrors = (List<?>) changeResponse.getBody(Map.class).orElseThrow().get("passwordErrors");
        assertNotNull(passwordErrors, "a refused password must say which rules it broke");
        assertFalse(passwordErrors.isEmpty(), "a refused password must say which rules it broke");
        assertEquals(Map.of("MIN_LENGTH_NOT_MET", 5), passwordErrors.getFirst());
    }

    private String accessTokenFor(String password) {
        HttpResponse<Map> authenticated = authenticate(password);
        assertEquals(HttpStatus.OK, authenticated.getStatus());
        return (String) authenticated.getBody(Map.class).orElseThrow().get("accessToken");
    }

    private HttpResponse<Map> authenticate(String password) {
        return exchange(HttpRequest.POST("/authenticate", Map.of("email", email, "password", password)));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Map> exchange(HttpRequest<?> request) {
        try {
            return client.exchange(request, Map.class);
        } catch (HttpClientResponseException e) {
            return (HttpResponse<Map>) e.getResponse();
        }
    }

    private void verifySeededUser(String email) {
        // sign-in requires a verified address, so seeding completes onboarding with the e-mailed token
        String token = server.getApplicationContext()
                .getBean(CapturingEmailVerificationNotifier.class).lastTokenFor(email);
        assertNotNull(token, "no verification link was e-mailed on registration");
        HttpResponse<Map> verified = exchange(HttpRequest.POST("/verify-email", Map.of("token", token)));
        assertEquals(HttpStatus.OK, verified.getStatus(), "failed to verify the seeded user");
    }
}
