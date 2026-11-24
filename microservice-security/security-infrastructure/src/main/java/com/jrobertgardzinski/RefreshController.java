package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.session.SessionService;
import com.jrobertgardzinski.security.application.TransactionBoundary;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

import java.util.Map;
import java.util.Optional;

/**
 * HTTP entry point for session refresh. Drives the same {@code RefreshSession} use case as the
 * application-level Cucumber glue — shared behaviour, different entry point. The refresh token
 * travels only in the {@code HttpOnly} cookie (see {@link RefreshCookies}); the user is found from
 * the token, so the client never names itself.
 *
 * <p>The HTTP contract:
 * <ul>
 *   <li>{@code Refreshed} &rarr; 200 OK, a new access token in the body and a rotated refresh
 *       cookie in {@code Set-Cookie}</li>
 *   <li>{@code Expired} / {@code NotFound} / no cookie &rarr; 401 Unauthorized (uniform — the
 *       response does not reveal whether the token existed)</li>
 * </ul>
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/refresh")
public class RefreshController {

    private final SessionService sessionService;
    private final RefreshCookies refreshCookies;

    public RefreshController(SessionService sessionService, RefreshCookies refreshCookies) {
        this.sessionService = sessionService;
        this.refreshCookies = refreshCookies;
    }

    @Post(consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> refresh(HttpRequest<?> request) {
        Optional<String> presentedToken = refreshCookies.read(request);
        if (presentedToken.isEmpty()) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED);
        }

        SessionService.RefreshOutcome outcome = sessionService.refresh(presentedToken.get());

        return switch (outcome) {
            case SessionService.RefreshOutcome.Refreshed refreshed ->
                    HttpResponse.ok(Map.<String, Object>of("accessToken", refreshed.accessToken()))
                            .cookie(refreshCookies.issue(refreshed.refreshToken()));
            // one answer for all three: why a refresh token stopped working is nobody's business
            // but the service's
            case SessionService.RefreshOutcome.Expired _, SessionService.RefreshOutcome.NotFound _,
                 SessionService.RefreshOutcome.ReuseDetected _ ->
                    HttpResponse.status(HttpStatus.UNAUTHORIZED);
        };
    }
}
