package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.passwordreset.PasswordResetService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry points for the forgotten-password flow, driving the {@code RequestPasswordReset} and
 * {@code ResetPassword} use cases. Public (pre-login): {@code POST /reset-password/request} mails a
 * link, {@code POST /reset-password} sets a new password with the token from that link. The
 * request side is throttled per source (429 + Retry-After) — it mints tokens and sends mails, so
 * unthrottled it is a mail-bomb aimed at any address the caller types in.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/reset-password")
final class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    PasswordResetController(PasswordResetService passwordResetService, ClientIpResolver clientIpResolver) {
        this.passwordResetService = passwordResetService;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(HttpRequest<?> httpRequest, @Body Map<String, Object> body) {
        PasswordResetService.RequestOutcome outcome = passwordResetService.requestLink(
                JsonBody.text(body, "email"),
                clientIpResolver.resolve(httpRequest).value());
        return switch (outcome) {
            // One answer for all three, so that nobody can probe who is registered here. A
            // malformed address must be just as quiet: a different answer for it would be a
            // different answer for SOME inputs, and it used to be the loudest one of all — a 500
            // quoting the domain back at the caller.
            case PasswordResetService.RequestOutcome.LinkSent _, PasswordResetService.RequestOutcome.UserNotFound _,
                 PasswordResetService.RequestOutcome.UnreadableEmail _ ->
                    HttpResponse.accepted().body(Map.of("status", "RESET_LINK_SENT"));
            case PasswordResetService.RequestOutcome.Throttled throttled ->
                    HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Map.of("error", "TOO_MANY_RESET_REQUESTS"));
        };
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> reset(@Body Map<String, Object> body) {
        PasswordResetService.ResetOutcome outcome = passwordResetService.reset(
                JsonBody.text(body, "token"),
                JsonBody.text(body, "password"));
        return switch (outcome) {
            case PasswordResetService.ResetOutcome.PasswordReset reset ->
                    HttpResponse.ok(Map.of("status", "PASSWORD_RESET", "email", reset.email()));
            // the password half of what /register answers: one code per broken rule, valued by the
            // parameter in force for this very attempt
            case PasswordResetService.ResetOutcome.Rejected rejected ->
                    HttpResponse.unprocessableEntity().body(Map.of(
                            "passwordErrors", SecurityController.wire(rejected.passwordErrors())));
            // one answer for all four: telling them apart would confirm to a stranger that the
            // address has, or had, an account
            case PasswordResetService.ResetOutcome.TokenNotFound _, PasswordResetService.ResetOutcome.TokenExpired _,
                 PasswordResetService.ResetOutcome.UserNotFound _, PasswordResetService.ResetOutcome.UnreadableToken _ ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}
