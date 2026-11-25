package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.admin.AdminService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Map;

/**
 * Admin-only: the password policy in force and the minimum length as a live decision. GET reports
 * EVERY rule of the policy under its key, each with its provenance: which level answered and
 * what was refused on the way, in the gate's own words — how an admin learns that a row written
 * at the database console, under any of the five keys, is not the value the system uses. POST
 * sets the minimum length: the one rule a film follows by name, under the rule's own key, so the
 * value object is the only gate and a refused length changes nothing. Every other rule goes
 * through {@link AdminSettingsController} by its key. The caller must be an ADMIN, and setting
 * the policy takes a fresh step-up, since it binds every future password.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/settings/password")
final class AdminPasswordPolicyController {

    private final AdminService adminService;

    AdminPasswordPolicyController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> report(HttpRequest<?> request) {
        return switch (adminService.passwordPolicy(RequestCaller.email(request))) {
            case AdminService.SettingsOutcome.Settings settings ->
                    HttpResponse.ok(AdminSettingsController.report(settings.settings()));
            case AdminService.SettingsOutcome.NotAnAdmin notAnAdmin ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "NOT_AN_ADMIN"));
        };
    }

    @Post(value = "/min-length", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> set(HttpRequest<?> request, @Body Map<String, Object> body) {
        Object told = body.get("value");
        AdminService.SetPasswordMinLengthOutcome outcome = adminService.setPasswordMinLength(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                told == null ? null : String.valueOf(told));
        return switch (outcome) {
            case AdminService.SetPasswordMinLengthOutcome.Accepted accepted ->
                    HttpResponse.ok(Map.of("status", "ACCEPTED", "value", accepted.value()));
            case AdminService.SetPasswordMinLengthOutcome.Refused refused ->
                    HttpResponse.badRequest(Map.of("status", "REFUSED", "reason", refused.reason()));
            case AdminService.SetPasswordMinLengthOutcome.NotANumber notANumber ->
                    HttpResponse.badRequest(Map.of("status", "NOT_A_NUMBER"));
            case AdminService.SetPasswordMinLengthOutcome.NotAnAdmin notAnAdmin ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "NOT_AN_ADMIN"));
            case AdminService.SetPasswordMinLengthOutcome.StepUpRequired stepUp ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "STEP_UP_REQUIRED", "action", stepUp.action()));
        };
    }
}
