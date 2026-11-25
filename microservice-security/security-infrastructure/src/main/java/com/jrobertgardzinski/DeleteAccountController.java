package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.account.AccountDeletionService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.jrobertgardzinski.MaskedEmail.masked;

/**
 * The ONE door out of an account, for the person whose account it is and for an administrator
 * closing somebody else's. Which of the two it is comes from the request itself — the address in
 * the path against the address in the token — and {@link AccountDeletionService} decides
 * everything that follows from that comparison: what must be proven, and whether the caller may
 * say what happens to the content.
 *
 * <p>The two used to be two routes. One is closer to the truth: "am I closing my own account or
 * someone else's" is a property of the request, not of the URL, and the authorisation is what
 * follows from it rather than what picks it.
 *
 * <p>Protected: {@link AuthorizationFilter} has authorized the access token and published the
 * caller's email. The answer is 202, not 200: closing is a saga, and the content services'
 * confirmation — not this request — deletes the account for good.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/account")
final class DeleteAccountController {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteAccountController.class);

    private final AccountDeletionService accountDeletionService;

    DeleteAccountController(AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
    }

    @Delete(value = "/{email}", consumes = MediaType.ALL, produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> delete(HttpRequest<?> request, @PathVariable String email,
                                             @Body @Nullable Map<String, Map<String, String>> body) {
        AccountDeletionService.StartOutcome outcome = accountDeletionService.start(
                RequestCaller.email(request),
                RequestCaller.bearerToken(request),
                email,
                body == null ? null : body.get("purge"));
        return switch (outcome) {
            case AccountDeletionService.StartOutcome.OwnAccountClosing own ->
                    HttpResponse.<Map<String, Object>>accepted().body(Map.of("status", "ACCOUNT_DELETION_STARTED"));
            case AccountDeletionService.StartOutcome.SomebodyElsesClosing closing -> {
                // the audit line, and the only place the two addresses ever meet: the fact on the
                // wire says ADMIN and not WHICH admin, because three content services would then
                // hold an extra person's address for no use of their own
                LOG.warn("account deletion started for {} by administrator {}", masked(closing.user()),
                        masked(closing.administrator()));
                yield HttpResponse.<Map<String, Object>>accepted().body(Map.of("status", "ACCOUNT_DELETION_STARTED"));
            }
            case AccountDeletionService.StartOutcome.InvalidEmail invalid ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("status", "INVALID_EMAIL"));
            case AccountDeletionService.StartOutcome.NotAnAdmin notAnAdmin ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "NOT_AN_ADMIN"));
            case AccountDeletionService.StartOutcome.InvalidPurgeChoices oversized ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(Map.of("status", "INVALID_PURGE_CHOICES"));
            case AccountDeletionService.StartOutcome.NoSuchUser noSuchUser ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.NOT_FOUND)
                            .body(Map.of("status", "NO_SUCH_USER"));
            case AccountDeletionService.StartOutcome.StepUpRequired stepUp ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.FORBIDDEN)
                            .body(Map.of("status", "STEP_UP_REQUIRED", "action", stepUp.action()));
        };
    }
}
