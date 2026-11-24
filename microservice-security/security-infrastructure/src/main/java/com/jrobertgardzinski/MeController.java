package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.identity.IdentityService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Get;

import java.util.List;
import java.util.Map;

/**
 * A protected resource: returns who the caller is AND what roles they hold — the source of truth
 * other services read to gate moderator/admin actions. Reaching this controller means
 * {@link AuthorizationFilter} already authorized the access token and published the email; here we
 * read it back and look up the user's roles.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/me")
final class MeController {

    private final IdentityService identityService;

    MeController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> me(HttpRequest<?> request) {
        IdentityService.Me me = identityService.me(RequestCaller.email(request));
        return HttpResponse.ok(Map.of(
                "id", me.id(),
                "email", me.email(),
                "roles", me.roles(),
                "mfaCompliant", me.mfaCompliant(),
                "requiredFactors", me.requiredFactors(),
                "haveFactors", me.haveFactors()));
    }
}
