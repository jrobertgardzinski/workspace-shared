package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.admin.AdminService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Map;

/**
 * Admin-only: grant or revoke another user's roles. {@link AuthorizationFilter} has already
 * authorized the caller; {@link AdminService} adds the second gate — the caller must themselves be
 * an ADMIN, from a persisted grant or the deployment's bootstrap list, which breaks the
 * chicken-and-egg: a bootstrap admin is ADMIN before any grant and can hand out roles to everyone
 * else — and the third, a fresh step-up.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/admin/users")
final class AdminRolesController {

    private final AdminService adminService;

    AdminRolesController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Put(value = "/{email}/roles", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> setRoles(HttpRequest<?> request, @PathVariable String email, @Body Map<String, Object> body) {
        AdminService.SetRolesOutcome outcome = adminService.setRoles(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                email,
                names(body.get("roles")));
        return switch (outcome) {
            case AdminService.SetRolesOutcome.Updated updated ->
                    HttpResponse.ok(Map.of("email", updated.user(), "roles", updated.roles()));
            case AdminService.SetRolesOutcome.NoSuchUser noSuchUser ->
                    HttpResponse.notFound(Map.of("status", "NO_SUCH_USER"));
            // 409, not 403: the caller IS allowed to do this, and the state of the system is what
            // refuses. Telling them which is the difference between "try again with proof" and
            // "grant somebody else first".
            case AdminService.SetRolesOutcome.WouldLeaveNoAdmin refused ->
                    HttpResponse.status(HttpStatus.CONFLICT)
                            .body(Map.of("status", "WOULD_LEAVE_NO_ADMIN", "roles", refused.roles()));
            case AdminService.SetRolesOutcome.NotAnAdmin notAnAdmin ->
                    HttpResponse.status(HttpStatus.FORBIDDEN).body(Map.of("status", "NOT_AN_ADMIN"));
            case AdminService.SetRolesOutcome.InvalidEmail invalid ->
                    HttpResponse.badRequest(Map.of("status", "INVALID_EMAIL"));
            case AdminService.SetRolesOutcome.UnknownRole unknown ->
                    HttpResponse.badRequest(Map.of("status", "UNKNOWN_ROLE", "roles", unknown.known()));
            case AdminService.SetRolesOutcome.StepUpRequired stepUp ->
                    HttpResponse.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "STEP_UP_REQUIRED", "action", stepUp.action()));
        };
    }

    /** The names as the JSON carried them, each read as its text; null when it carried no list. */
    private static List<String> names(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        return list.stream().map(String::valueOf).toList();
    }
}
