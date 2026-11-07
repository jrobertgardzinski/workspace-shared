package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.RoleStatus;
import com.jrobertgardzinski.security.system.roles.RequireRole;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;

/**
 * The role gate an admin endpoint puts in front of itself: the caller must hold the role, from a
 * persisted grant or the deployment's bootstrap list. Refused → 403 with the rule's own code
 * ({@code NOT_AN_ADMIN}), so a non-admin learns only that they are not one.
 */
@Singleton
public final class RoleGuard {

    private final RequireRole requireRole;

    RoleGuard(RequireRole requireRole) {
        this.requireRole = requireRole;
    }

    /** A 403 response if the caller lacks the role, otherwise empty (proceed). */
    public Optional<HttpResponse<Map<String, Object>>> require(HttpRequest<?> request, Role role) {
        RoleStatus status = requireRole.check(Caller.of(request), role);
        return switch (status) {
            case RoleStatus.Passed _ -> Optional.empty();
            case RoleStatus.NotAUser _ -> forbidden("NOT_A_USER");
            case RoleStatus.NotAModerator _ -> forbidden("NOT_A_MODERATOR");
            case RoleStatus.NotAnAdmin _ -> forbidden("NOT_AN_ADMIN");
        };
    }

    private static Optional<HttpResponse<Map<String, Object>>> forbidden(String code) {
        return Optional.of(
                HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                        .body(Map.of("status", code)));
    }
}
