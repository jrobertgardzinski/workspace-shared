package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.account.AccountService;
import com.jrobertgardzinski.security.application.TransactionBoundary;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Named;

import java.util.Map;

/**
 * HTTP entry point for changing a password. A protected endpoint: {@link AuthorizationFilter} has
 * already authorized the access token and published the caller's email, so we change that user's
 * password once their current password checks out, driving the {@code ChangePassword} use case.
 *
 * <p>Authorized is not the same as unlimited. Checking the current password runs a full Argon2 and
 * answers whether the guess was right, so with a stolen access token this endpoint is a password
 * oracle — and a hit is a takeover, because changing the password revokes every session, the
 * owner's included. Hence a per-source window of its own, the same shape the anonymous endpoints
 * and the step-up already use.
 *
 * <p>A wrong current password answers {@code 400 FAILED_AUTHENTICATION_ATTEMPT}. An account that
 * has gone from under the live session ({@code AccountGone}: deleted while the token still worked)
 * answers {@code 401 ACCOUNT_GONE}: there is nothing left to change, and a 401 is what makes the
 * UI sign out. The two used to share one 400, copied from the sign-in, where hiding which half
 * was wrong protects against enumeration; a caller holding a live token is not enumerating.
 *
 * <p>A new password the policy refuses answers 422 with {@code {"passwordErrors": [{"CODE":
 * parameter}]}}, the password half of what {@code /register} answers: one code per broken rule,
 * valued by the parameter in force for this very attempt.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/password")
final class ChangePasswordController {

    private final AccountService accountService;
    private final ClientIpResolver clientIpResolver;

    ChangePasswordController(AccountService accountService, ClientIpResolver clientIpResolver) {
        this.accountService = accountService;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> change(HttpRequest<?> request, @Body Map<String, Object> body) {
        AccountService.ChangePasswordOutcome outcome = accountService.changePassword(
                RequestCaller.email(request),
                JsonBody.text(body, "currentPassword"),
                JsonBody.text(body, "newPassword"),
                clientIpResolver.resolve(request).value());
        return switch (outcome) {
            case AccountService.ChangePasswordOutcome.Changed _ ->
                    HttpResponse.ok(Map.of("status", "PASSWORD_CHANGED"));
            case AccountService.ChangePasswordOutcome.WrongPassword _ ->
                    HttpResponse.badRequest().body(Map.of("status", "FAILED_AUTHENTICATION_ATTEMPT"));
            case AccountService.ChangePasswordOutcome.AccountGone _ ->
                    HttpResponse.unauthorized().body(Map.of("status", "ACCOUNT_GONE"));
            case AccountService.ChangePasswordOutcome.Rejected rejected ->
                    HttpResponse.unprocessableEntity().body(Map.of(
                            "passwordErrors", SecurityController.wire(rejected.passwordErrors())));
            case AccountService.ChangePasswordOutcome.MissingPassword _ ->
                    HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
            case AccountService.ChangePasswordOutcome.Throttled throttled ->
                    HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Map.of("status", "TOO_MANY_ATTEMPTS"));
        };
    }
}
