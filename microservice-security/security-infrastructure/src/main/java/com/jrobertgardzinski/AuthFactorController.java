package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.authentication.AuthenticationService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * The second (and further) steps of a multi-factor sign-in: the client presents a proof against the
 * ticket it got from {@code /authenticate}. Completing the chain returns the same session shape as a
 * single-factor sign-in (access token in the body, refresh token in the HttpOnly cookie); a wrong
 * proof reports how many tries remain; running out or an unknown ticket ends the attempt.
 *
 * <p>This endpoint shares one per-source window with {@code /authenticate} itself; why is said
 * where the window is kept, in {@link AuthenticationService}.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/authenticate/factor")
final class AuthFactorController {

    private final AuthenticationService authenticationService;
    private final RefreshCookies refreshCookies;
    private final ClientIpResolver clientIpResolver;

    AuthFactorController(AuthenticationService authenticationService, RefreshCookies refreshCookies,
                         ClientIpResolver clientIpResolver) {
        this.authenticationService = authenticationService;
        this.refreshCookies = refreshCookies;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> submit(HttpRequest<?> request, @Body Map<String, String> body) {
        AuthenticationService.FactorOutcome outcome = authenticationService.presentFactor(
                JsonBody.text(body, "mfaTicket"),
                JsonBody.text(body, "proof"),
                clientIpResolver.resolve(request).value());

        return switch (outcome) {
            case AuthenticationService.FactorOutcome.Authenticated authenticated ->
                    HttpResponse.ok(Map.<String, Object>of("accessToken", authenticated.accessToken()))
                            .cookie(refreshCookies.issue(authenticated.refreshToken()));
            case AuthenticationService.FactorOutcome.NextFactor next ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.ACCEPTED)
                            .body(MfaBody.of(next.ticket(), next.nextFactor(), next.challengeData()));
            case AuthenticationService.FactorOutcome.WrongProof wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_CODE", "attemptsLeft", wrong.attemptsLeft()));
            case AuthenticationService.FactorOutcome.TooManyAttempts tooMany ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "TOO_MANY_ATTEMPTS"));
            case AuthenticationService.FactorOutcome.InvalidTicket invalid ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "INVALID_OR_EXPIRED_TICKET"));
            case AuthenticationService.FactorOutcome.MissingTicketOrProof missing ->
                    HttpResponse.<Map<String, Object>>badRequest().body(Map.of("status", "BAD_REQUEST"));
            case AuthenticationService.FactorOutcome.Throttled throttled ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Map.of("status", "TOO_MANY_ATTEMPTS"));
        };
    }
}
