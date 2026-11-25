package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.session.SessionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry point for logout. Ends the session named by the refresh-token cookie (driving the same
 * {@code Logout} use case as any other entry point) and clears the cookie. Idempotent: with no
 * cookie there is nothing to end, and the response still succeeds and clears the cookie.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/logout")
final class LogoutController {

    private final SessionService sessionService;
    private final RefreshCookies refreshCookies;

    LogoutController(SessionService sessionService, RefreshCookies refreshCookies) {
        this.sessionService = sessionService;
        this.refreshCookies = refreshCookies;
    }

    @Post(consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> logout(HttpRequest<?> request) {
        refreshCookies.read(request).ifPresent(sessionService::logout);
        return HttpResponse.ok(Map.<String, Object>of("status", "LOGGED_OUT")).cookie(refreshCookies.clear());
    }
}
