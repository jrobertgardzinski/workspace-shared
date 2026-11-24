package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.account.AccountService;
import com.jrobertgardzinski.security.application.TransactionBoundary;

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
 * the link is not signed in yet). Drives the {@code ConfirmEmailChange} use case.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/confirm-email-change")
final class ConfirmEmailChangeController {

    private final AccountService accountService;

    ConfirmEmailChangeController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> confirm(@Body Map<String, Object> body) {
        AccountService.ConfirmEmailChangeOutcome outcome =
                accountService.confirmEmailChange(JsonBody.text(body, "token"));
        return switch (outcome) {
            case AccountService.ConfirmEmailChangeOutcome.EmailChanged changed ->
                    HttpResponse.ok(Map.of("status", "EMAIL_CHANGED", "email", changed.newEmail()));
            // 409: the request was well formed and the token was good — the world moved. Its own
            // status, because "invalid token" would send the owner to fetch another one that fails
            // exactly the same way.
            case AccountService.ConfirmEmailChangeOutcome.EmailTaken _ ->
                    HttpResponse.status(io.micronaut.http.HttpStatus.CONFLICT)
                            .body(Map.of("status", "EMAIL_TAKEN"));
            // one answer for all four: telling them apart would tell a stranger that a change to
            // this address was once started, or that the account behind it is being closed
            case AccountService.ConfirmEmailChangeOutcome.TokenNotFound _,
                 AccountService.ConfirmEmailChangeOutcome.TokenExpired _,
                 AccountService.ConfirmEmailChangeOutcome.AccountClosing _,
                 AccountService.ConfirmEmailChangeOutcome.UnreadableToken _ ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}
