package com.jrobertgardzinski;

import com.jrobertgardzinski.security.application.verification.VerificationService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Named;

import java.util.Map;

/**
 * HTTP entry points for e-mail verification, driving the {@code RequestEmailVerification} and
 * {@code VerifyEmail} use cases. Public (pre-login): {@code POST /verify-email/request} mails a
 * link, {@code POST /verify-email} confirms the token from that link. The request side is
 * throttled per source (429 + Retry-After) — it mints tokens and sends mails, so unthrottled it
 * is a mail-bomb aimed at any address the caller types in.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/verify-email")
final class VerifyEmailController {

    private final VerificationService verificationService;
    private final ClientIpResolver clientIpResolver;

    VerifyEmailController(VerificationService verificationService, ClientIpResolver clientIpResolver) {
        this.verificationService = verificationService;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(value = "/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> request(HttpRequest<?> httpRequest, @Body Map<String, Object> body) {
        VerificationService.RequestOutcome outcome = verificationService.requestLink(
                JsonBody.text(body, "email"),
                clientIpResolver.resolve(httpRequest).value());
        return switch (outcome) {
            // One answer for all three, so that nobody can probe who is registered or verified
            // here. A malformed address must be just as quiet: a different answer for it would be
            // a different answer for SOME inputs, and it used to be the loudest one of all — a 500
            // quoting the domain back at the caller.
            case VerificationService.RequestOutcome.LinkSent _, VerificationService.RequestOutcome.AlreadyVerified _,
                 VerificationService.RequestOutcome.UnreadableEmail _ ->
                    HttpResponse.accepted().body(Map.of("status", "VERIFICATION_LINK_SENT"));
            case VerificationService.RequestOutcome.Throttled throttled ->
                    HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Refusal.alsoAsError("TOO_MANY_VERIFICATION_REQUESTS"));
        };
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> verify(@Body Map<String, Object> body) {
        VerificationService.VerifyOutcome outcome = verificationService.verify(JsonBody.text(body, "token"));
        return switch (outcome) {
            case VerificationService.VerifyOutcome.Verified verified ->
                    HttpResponse.ok(Map.of("status", "EMAIL_VERIFIED", "email", verified.email()));
            // one answer for all three: telling them apart would tell a stranger that a
            // verification for this address was once started
            case VerificationService.VerifyOutcome.TokenNotFound _, VerificationService.VerifyOutcome.TokenExpired _,
                 VerificationService.VerifyOutcome.UnreadableToken _ ->
                    HttpResponse.badRequest().body(Map.of("status", "INVALID_TOKEN"));
        };
    }
}
