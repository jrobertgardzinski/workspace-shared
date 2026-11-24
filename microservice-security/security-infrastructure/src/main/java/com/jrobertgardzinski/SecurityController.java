package com.jrobertgardzinski;

import com.jrobertgardzinski.email.config.EmailPolicy;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.application.BrokenRule;
import com.jrobertgardzinski.security.application.registration.RegistrationService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Map;

/**
 * HTTP entry point for registration. It reads the request into plain strings, hands them to
 * {@link RegistrationService}, and renders the outcome; what a registration IS lives below it.
 * The HTTP contract:
 * <ul>
 *   <li>{@code Registered}        &rarr; 201 Created, {@code {"status": "CHECK_YOUR_MAILBOX"}};
 *       a verification link is e-mailed — sign-in stays blocked until the address is verified</li>
 *   <li>{@code Rejected}          &rarr; 422 Unprocessable Entity,
 *       {@code {"emailErrors": [{"CODE": parameter}], "passwordErrors": [{"CODE": parameter}]}} —
 *       both channels speak ONE shape: one code per broken rule, valued by the parameter in force
 *       for this very attempt ({@code {"MIN_LENGTH_NOT_MET": 12}},
 *       {@code {"NOT_A_COMPANY_DOMAIN": ["acme.com"]}}) or {@code true} where the rule has none.
 *       Both policies are configuration, so a bare code would leave the caller guessing what the
 *       minimum was, or which domains an employee may register from</li>
 *   <li>{@code EmailAlreadyTaken} &rarr; the same 201 and the same body as {@code Registered}
 *       (anti-enumeration: the wire never confirms an account exists). What differs is the mail,
 *       readable only by the address owner: a still-unverified account gets a fresh verification
 *       link (they probably lost the first one), a verified one gets a "you already have an
 *       account" notice.</li>
 * </ul>
 * 422 (not 400): the JSON is well-formed; the failure is semantic validation of the values.
 * Validation errors may reveal that an address is malformed, but never whether it is taken.
 */
// controllers do blocking work (JDBC, the mail service's HTTP client) — keep it off the event loop
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/register")
public class SecurityController {

    /** The one body every non-rejected registration answers with — fresh or taken alike. */
    static final Map<String, Object> CHECK_YOUR_MAILBOX = Map.of("status", "CHECK_YOUR_MAILBOX");

    private final RegistrationService registrationService;
    private final ClientIpResolver clientIpResolver;

    public SecurityController(RegistrationService registrationService, ClientIpResolver clientIpResolver) {
        this.registrationService = registrationService;
        this.clientIpResolver = clientIpResolver;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> register(HttpRequest<?> request, @Body Map<String, String> body) {
        RegistrationService.Outcome outcome = registrationService.register(
                body.get("email"),
                body.get("password"),
                clientIpResolver.resolve(request).value());

        return switch (outcome) {
            // anti-enumeration: a taken address answers exactly like a fresh one
            case RegistrationService.Outcome.Registered _, RegistrationService.Outcome.EmailAlreadyTaken _ ->
                    HttpResponse.<Map<String, Object>>created(CHECK_YOUR_MAILBOX);
            case RegistrationService.Outcome.Rejected rejected ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(Map.of(
                                    "emailErrors", wire(rejected.emailErrors()),
                                    "passwordErrors", wire(rejected.passwordErrors())));
            case RegistrationService.Outcome.Throttled throttled ->
                    HttpResponse.<Map<String, Object>>status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", String.valueOf(throttled.retryAfterSeconds()))
                            .body(Refusal.alsoAsError("TOO_MANY_REGISTRATIONS"));
        };
    }

    /**
     * The JSON shape of broken rules, one for both channels so a client renders them with one piece
     * of code: one entry per rule, keyed by its code and valued by its parameter
     * ({@code [{"MIN_LENGTH_NOT_MET": 12}]}).
     */
    static List<Map<String, Object>> wire(List<BrokenRule> rules) {
        return rules.stream().map(rule -> Map.of(rule.code(), rule.parameter())).toList();
    }

    /** For the controllers that still read a use case's result themselves. */
    static List<Map<String, Object>> emailErrors(List<EmailError> errors, EmailPolicy policy) {
        return wire(BrokenRule.ofEmail(errors, policy));
    }

    /** For the controllers that still read a use case's result themselves. */
    static List<Map<String, Object>> passwordErrors(List<PasswordError> errors, PasswordPolicy policy) {
        return wire(BrokenRule.ofPassword(errors, policy));
    }
}
