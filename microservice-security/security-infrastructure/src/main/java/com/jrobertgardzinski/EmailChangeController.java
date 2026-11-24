package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.account.AccountService;
import io.micronaut.http.HttpStatus;
import com.jrobertgardzinski.security.application.TransactionBoundary;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;

import java.util.Map;

/**
 * HTTP entry point to start an email change. Protected: {@link AuthorizationFilter} has authorized
 * the access token and published the caller's current email; here we request a change to the new
 * address, which e-mails a verification link there (confirmed separately, pre-login).
 *
 * <p>Same anti-enumeration stance as {@code /register}: a taken address answers exactly like a
 * fresh request, and the OWNER of that address learns by mail that someone tried to use it. A
 * signed-in caller probing addresses gets nothing the anonymous one would not.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account/email")
final class EmailChangeController {

    private final AccountService accountService;

    EmailChangeController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(HttpRequest<?> request, @Body Map<String, Object> body) {
        AccountService.RequestEmailChangeOutcome outcome = accountService.requestEmailChange(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                JsonBody.text(body, "newEmail"));
        return switch (outcome) {
            // quiet refusal: a taken address looks like a fresh request on the wire; the address
            // owner is told by mail
            case AccountService.RequestEmailChangeOutcome.Requested _,
                 AccountService.RequestEmailChangeOutcome.EmailTaken _ ->
                    HttpResponse.accepted().body(Map.of("status", "EMAIL_CHANGE_LINK_SENT"));
            case AccountService.RequestEmailChangeOutcome.InvalidEmail _ ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_EMAIL"));
            case AccountService.RequestEmailChangeOutcome.StepUpRequired stepUp ->
                    HttpResponse.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "STEP_UP_REQUIRED", "action", stepUp.action()));
            // the policy's refusal is NOT quiet: it is about the address the caller typed, not
            // about who else holds it, so it says which rule was broken — exactly as /register does
            case AccountService.RequestEmailChangeOutcome.Rejected rejected ->
                    HttpResponse.unprocessableEntity().body(Map.of(
                            "emailErrors", SecurityController.wire(rejected.emailErrors())));
        };
    }
}
