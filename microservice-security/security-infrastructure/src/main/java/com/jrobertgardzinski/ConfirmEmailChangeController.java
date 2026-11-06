package com.jrobertgardzinski;

import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChange;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * Public HTTP entry point to confirm an email change with the token from the link (the recipient of
 * the link is not signed in yet). Drives the {@link ConfirmEmailChange} use case.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/confirm-email-change")
final class ConfirmEmailChangeController {

    private final ConfirmEmailChange confirmEmailChange;
    private final TransactionBoundary transactionBoundary;

    ConfirmEmailChangeController(ConfirmEmailChange confirmEmailChange, TransactionBoundary transactionBoundary) {
        this.confirmEmailChange = confirmEmailChange;
        this.transactionBoundary = transactionBoundary;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> confirm(@Body Map<String, Object> body) {
        VerificationToken token;
        try {
            token = new VerificationToken(JsonBody.text(body, "token"));
        } catch (IllegalArgumentException missingOrBlank) {
            return HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        }
        // nobody else is told: the rest of the estate keys a person's rows on their id, and shows
        // the address by asking this service (GET /users?ids=)
        ConfirmEmailChange.Result result = transactionBoundary.execute(() -> confirmEmailChange.execute(token));
        return switch (result) {
            case ConfirmEmailChange.Result.EmailChanged changed ->
                    HttpResponse.ok(Map.of("status", "EMAIL_CHANGED", "email", changed.newEmail().value()));
            // 409: the request was well formed and the token was good — the world moved. Its own
            // status, because "invalid token" would send the owner to fetch another one that fails
            // exactly the same way.
            case ConfirmEmailChange.Result.EmailTaken ignored ->
                    HttpResponse.status(io.micronaut.http.HttpStatus.CONFLICT)
                            .body(Map.of("status", "EMAIL_TAKEN"));
            // one answer for all three: telling them apart would tell a stranger that a change to
            // this address was once started, or that the account behind it is being closed
            case ConfirmEmailChange.Result.TokenNotFound _, ConfirmEmailChange.Result.TokenExpired _,
                 ConfirmEmailChange.Result.AccountClosing _ ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}
