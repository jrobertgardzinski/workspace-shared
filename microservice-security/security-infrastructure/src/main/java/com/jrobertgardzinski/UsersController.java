package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.identity.IdentityService;
import com.jrobertgardzinski.identity.UserId;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Named;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The names behind a batch of user ids — what a gallery or a thread shows next to content it
 * holds by id. Anonymous, like the content itself; never the address, only its display form; an
 * id nobody holds is left out, so a deleted account and an invented id look the same.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/users")
final class UsersController {

    private final IdentityService identityService;
    private final ClientIpResolver ipResolver;

    UsersController(IdentityService identityService, ClientIpResolver ipResolver) {
        this.identityService = identityService;
        this.ipResolver = ipResolver;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> names(HttpRequest<?> request, @QueryValue(defaultValue = "") String ids) {
        IdentityService.DisplayNamesOutcome outcome =
                identityService.displayNames(ids, ipResolver.resolve(request).value());
        return switch (outcome) {
            case IdentityService.DisplayNamesOutcome.Names names ->
                    HttpResponse.ok(names.users().stream()
                            .map(user -> {
                                Map<String, String> entry = new java.util.LinkedHashMap<>();
                                entry.put("id", user.id());
                                entry.put("displayName", user.displayName());
                                return entry;
                            })
                            .toList());
            case IdentityService.DisplayNamesOutcome.InvalidId _ ->
                    HttpResponse.badRequest(Map.of("status", "INVALID_ID"));
            case IdentityService.DisplayNamesOutcome.TooManyIds tooMany ->
                    HttpResponse.badRequest(Map.of("status", "TOO_MANY_IDS", "max", tooMany.max()));
            case IdentityService.DisplayNamesOutcome.Throttled throttled ->
                    HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Refusal.alsoAsError("TOO_MANY_ATTEMPTS"));
        };
    }
}
