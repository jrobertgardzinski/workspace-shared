package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.mfa.MfaService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * Manage a signed-in user's MFA factors — the per-user half of the configuration. Enrolling proves
 * control of the new factor in two steps ({@code enroll/start} sends a challenge, {@code
 * enroll/confirm} accepts one proof). {@link AuthorizationFilter} has already authorized the caller
 * and published their e-mail; every action is against their own factors.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/factors")
final class FactorsController {

    private final MfaService mfaService;

    FactorsController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> list(HttpRequest<?> request) {
        MfaService.Factors factors = mfaService.factors(RequestCaller.email(request));
        return HttpResponse.ok(Map.of(
                "have", factors.have().stream()
                        .map(factor -> Map.of("type", factor.type(), "label", factor.label()))
                        .toList(),
                "offered", factors.offered()));
    }

    @Post(value = "/{type}/enroll/start", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> start(HttpRequest<?> request, @PathVariable String type,
                                            @Nullable @Body Map<String, String> body) {
        MfaService.StartEnrolmentOutcome outcome = mfaService.startEnrolment(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                type,
                body == null ? null : body.get("target"));
        return switch (outcome) {
            // a code factor sent a code (no display); a possession factor returns what to show (TOTP URI)
            case MfaService.StartEnrolmentOutcome.CodeSent sent ->
                    HttpResponse.<Map<String, Object>>accepted().body(Map.of("status", "ENROLL_CODE_SENT"));
            case MfaService.StartEnrolmentOutcome.SetupReady ready ->
                    HttpResponse.<Map<String, Object>>accepted()
                            .body(Map.of("status", "ENROLL_SETUP", "display", ready.display()));
            case MfaService.StartEnrolmentOutcome.UnsupportedFactor unsupported ->
                    HttpResponse.badRequest(Map.of("status", "UNSUPPORTED_FACTOR"));
            case MfaService.StartEnrolmentOutcome.UnreadableFactorType unreadable ->
                    HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
            case MfaService.StartEnrolmentOutcome.StepUpRequired stepUp ->
                    stepUpRequired(stepUp.action());
        };
    }

    @Post(value = "/{type}/enroll/confirm", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> confirm(HttpRequest<?> request, @PathVariable String type,
                                              @Body Map<String, String> body) {
        MfaService.ConfirmEnrolmentOutcome outcome = mfaService.confirmEnrolment(
                RequestCaller.email(request),
                type,
                body.get("code"));
        return switch (outcome) {
            case MfaService.ConfirmEnrolmentOutcome.Enrolled enrolled ->
                    HttpResponse.ok(Map.of("status", "ENROLLED", "type", enrolled.type()));
            case MfaService.ConfirmEnrolmentOutcome.WrongProof wrong ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "WRONG_CODE"));
            case MfaService.ConfirmEnrolmentOutcome.NoPendingEnrolment none ->
                    HttpResponse.badRequest(Map.of("status", "NO_PENDING_ENROLMENT"));
            case MfaService.ConfirmEnrolmentOutcome.UnreadableFactorType unreadable ->
                    HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
        };
    }

    @Delete(value = "/{type}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> remove(HttpRequest<?> request, @PathVariable String type) {
        MfaService.RemoveFactorOutcome outcome = mfaService.removeFactor(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                type);
        return switch (outcome) {
            case MfaService.RemoveFactorOutcome.Removed removed ->
                    HttpResponse.ok(Map.of("status", "REMOVED"));
            case MfaService.RemoveFactorOutcome.WouldBreakFloor floor ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.CONFLICT)
                            .body(Map.of("status", "WOULD_BREAK_MFA_FLOOR"));
            case MfaService.RemoveFactorOutcome.UnreadableFactorType unreadable ->
                    HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
            case MfaService.RemoveFactorOutcome.StepUpRequired stepUp ->
                    stepUpRequired(stepUp.action());
        };
    }

    private static HttpResponse<Map<String, Object>> stepUpRequired(String action) {
        return HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "STEP_UP_REQUIRED", "action", action));
    }
}
