package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.mfa.StepUpService;
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
 * Step-up authentication: re-prove yourself for a sensitive action. {@code POST /account/step-up}
 * begins it (the per-action policy decides whether a password and/or factors are needed);
 * {@code POST /account/step-up/factor} walks the factor chain. Passing it elevates the caller's
 * access token for a short window, which the sensitive endpoint then consumes. {@link
 * AuthorizationFilter} has already authorized the caller and published the email.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/step-up")
final class StepUpController {

    private final StepUpService stepUpService;

    StepUpController(StepUpService stepUpService) {
        this.stepUpService = stepUpService;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> start(HttpRequest<?> request, @Body Map<String, String> body) {
        StepUpService.StartOutcome outcome = stepUpService.start(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                body.get("action"),
                body.get("password"));
        return switch (outcome) {
            case StepUpService.StartOutcome.Elevated elevated ->
                    HttpResponse.ok(Map.of("status", "ELEVATED"));
            case StepUpService.StartOutcome.FactorRequired next ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.ACCEPTED)
                            .body(MfaBody.stepUp(next.ticket(), next.nextFactor(), next.challengeData()));
            case StepUpService.StartOutcome.WrongPassword wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_PASSWORD"));
            // the account was deleted while this token still worked: nothing to re-prove for,
            // and the 401 is what makes the UI sign out — the code says it is not a wrong password
            case StepUpService.StartOutcome.AccountGone gone ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "ACCOUNT_GONE"));
            // 409, not 401: nothing the caller can type would help. They are who they say they
            // are; the account simply carries nothing to re-prove with, and the way out is to
            // enrol a factor — which is a SECOND_FACTORS action and therefore still reachable.
            case StepUpService.StartOutcome.NothingToProveWith nothing ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.CONFLICT)
                            .body(Map.of("status", "ENROL_A_FACTOR_FIRST"));
            // an unknown name is a client error, not an elevation minted for something nobody
            // would ever consume
            case StepUpService.StartOutcome.UnknownAction unknown ->
                    HttpResponse.badRequest(Map.of("status", "UNKNOWN_ACTION"));
            case StepUpService.StartOutcome.Throttled throttled ->
                    tooManyAttempts(throttled.retryAfterSeconds());
        };
    }

    @Post(value = "/factor", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> factor(HttpRequest<?> request, @Body Map<String, String> body) {
        StepUpService.FactorOutcome outcome = stepUpService.presentFactor(
                RequestCaller.email(request),
                JsonBody.text(body, "stepUpTicket"),
                JsonBody.text(body, "proof"));
        return switch (outcome) {
            case StepUpService.FactorOutcome.Elevated elevated ->
                    HttpResponse.ok(Map.of("status", "ELEVATED"));
            case StepUpService.FactorOutcome.FactorRequired next ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.ACCEPTED)
                            .body(MfaBody.stepUp(next.ticket(), next.nextFactor(), next.challengeData()));
            case StepUpService.FactorOutcome.WrongProof wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_CODE", "attemptsLeft", wrong.attemptsLeft()));
            case StepUpService.FactorOutcome.TooManyAttempts tooMany ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "TOO_MANY_ATTEMPTS"));
            case StepUpService.FactorOutcome.InvalidTicket invalid ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "INVALID_TICKET"));
            case StepUpService.FactorOutcome.MissingTicketOrProof missing ->
                    HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
            case StepUpService.FactorOutcome.Throttled throttled ->
                    tooManyAttempts(throttled.retryAfterSeconds());
        };
    }

    /** A 429 that says when to come back. */
    private static HttpResponse<Map<String, Object>> tooManyAttempts(long retryAfterSeconds) {
        return HttpResponse.<Map<String, Object>>status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(Map.of("status", "TOO_MANY_STEP_UP_ATTEMPTS"));
    }
}
