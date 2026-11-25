package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.admin.AdminService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * Admin-only: reset another user's MFA factors — the recovery path when someone is locked out of
 * every factor (lost phone, etc.). The caller must be an ADMIN (a persisted role or the
 * deployment's bootstrap list) and must have just STEPPED UP, since wiping a user's factors drops
 * them below their role floor and forces re-enrolment. Both gates are {@link AdminService}'s.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/users")
final class AdminFactorsController {

    private final AdminService adminService;

    AdminFactorsController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Put(value = "/{email}/factors/reset", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> reset(HttpRequest<?> request, @PathVariable String email) {
        AdminService.ResetFactorsOutcome outcome = adminService.resetFactors(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                email);
        return switch (outcome) {
            case AdminService.ResetFactorsOutcome.FactorsReset reset ->
                    HttpResponse.ok(Map.of("status", "FACTORS_RESET", "user", reset.user()));
            case AdminService.ResetFactorsOutcome.NotAnAdmin notAnAdmin ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "NOT_AN_ADMIN"));
            case AdminService.ResetFactorsOutcome.InvalidEmail invalid ->
                    HttpResponse.badRequest(Map.of("status", "BAD_REQUEST"));
            case AdminService.ResetFactorsOutcome.StepUpRequired stepUp ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "STEP_UP_REQUIRED", "action", stepUp.action()));
        };
    }
}
