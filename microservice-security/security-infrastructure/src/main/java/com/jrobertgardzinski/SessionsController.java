package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.session.SessionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import java.util.List;
import java.util.Map;

/**
 * HTTP entry point for the caller's own sessions. A protected resource: {@link AuthorizationFilter}
 * has already authorized the access token and published the caller's email. {@code GET /sessions}
 * lists the active sessions; {@code POST /sessions/revoke-all} logs out everywhere (the presented
 * access token is itself one of the sessions revoked, so it stops working right after).
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/sessions")
final class SessionsController {

    private final SessionService sessionService;

    SessionsController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> list(HttpRequest<?> request) {
        List<Map<String, Object>> sessions = sessionService.list(RequestCaller.email(request)).stream()
                .map(SessionsController::toJson)
                .toList();
        return HttpResponse.ok(Map.of("sessions", sessions));
    }

    @Post(value = "/revoke-all", consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> revokeAll(HttpRequest<?> request) {
        sessionService.revokeAll(RequestCaller.email(request));
        return HttpResponse.ok(Map.of("status", "ALL_SESSIONS_REVOKED"));
    }

    private static Map<String, Object> toJson(SessionService.Session session) {
        return Map.of(
                "family", session.family().toString(),
                "expiresAt", session.expiresAt().toString());
    }
}
