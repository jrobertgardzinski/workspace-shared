package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.admin.AdminService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only: every rule declared live, by its key. GET lists the catalogue with what is in force
 * under each key and its provenance - the level that answered and what was refused on the way.
 * PUT tells one rule a value by its key: the catalogue supplies the rule's own parser and gate,
 * so there is no code per rule and no rule an ADMIN can reach that the system does not read.
 * The value arrives as text, the way a property or a row holds it; a number or a flag in the
 * JSON is read as its text. A key nobody declared is 404, a value the rule refuses is 400 with
 * the gate's reason, and in both cases nothing is written. The caller must be an ADMIN, and
 * every write takes a fresh step-up, since a rule binds every future decision under it.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/settings")
final class AdminSettingsController {

    private final AdminService adminService;

    AdminSettingsController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> catalogue(HttpRequest<?> request) {
        return switch (adminService.settings(RequestCaller.email(request))) {
            case AdminService.SettingsOutcome.Settings settings ->
                    HttpResponse.ok(report(settings.settings()));
            case AdminService.SettingsOutcome.NotAnAdmin notAnAdmin ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "NOT_AN_ADMIN"));
        };
    }

    @Put(value = "/{key}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> set(HttpRequest<?> request, @PathVariable String key, @Body Map<String, Object> body) {
        Object told = body.get("value");
        AdminService.SetSettingOutcome outcome = adminService.setSetting(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                key,
                told == null ? null : String.valueOf(told));
        return switch (outcome) {
            case AdminService.SetSettingOutcome.Accepted accepted ->
                    HttpResponse.ok(Map.of("status", "ACCEPTED", "key", key, "value", accepted.value()));
            case AdminService.SetSettingOutcome.Refused refused ->
                    HttpResponse.badRequest(Map.of("status", "REFUSED", "key", key, "reason", refused.reason()));
            case AdminService.SetSettingOutcome.UnknownKey unknown ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.NOT_FOUND)
                            .body(Map.of("status", "UNKNOWN_KEY", "key", key, "reason", unknown.reason()));
            case AdminService.SetSettingOutcome.NoValue noValue ->
                    HttpResponse.badRequest(Map.of("status", "NO_VALUE"));
            case AdminService.SetSettingOutcome.NotAnAdmin notAnAdmin ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "NOT_AN_ADMIN"));
            case AdminService.SetSettingOutcome.StepUpRequired stepUp ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "STEP_UP_REQUIRED", "action", stepUp.action()));
        };
    }

    /** One entry per rule, under its key and in the order given: value, source, what was refused. */
    static Map<String, Object> report(List<AdminService.Setting> settings) {
        Map<String, Object> report = new LinkedHashMap<>();
        for (AdminService.Setting setting : settings) {
            report.put(setting.key(), Map.of(
                    "value", setting.value(),
                    "source", setting.source(),
                    "rejected", setting.rejected().stream()
                            .map(refused -> Map.<String, Object>of(
                                    "source", refused.source(),
                                    "value", refused.value(),
                                    "reason", refused.reason()))
                            .toList()));
        }
        return report;
    }
}
