package com.jrobertgardzinski.security.application.passwordreset;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.FakeMailedLinkRepository;
import com.jrobertgardzinski.security.domain.repository.FakeSessionRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.system.passwordreset.RequestPasswordReset;
import com.jrobertgardzinski.security.system.passwordreset.ResetPassword;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is what an
 * unreadable address or token means, and when a source has asked too often.
 */
class PasswordResetServiceTest {

    private static final String SOURCE = "192.168.0.1";
    private static final String OWNER = "owner@example.com";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final FakeMailedLinkRepository links = new FakeMailedLinkRepository(clock);
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();
    /** The raw tokens that were "mailed", in order. */
    private final List<String> mailedTokens = new ArrayList<>();

    private final PasswordResetService service = service(5);

    private PasswordResetService service(int maxPerWindow) {
        return new PasswordResetService(
                new RequestPasswordReset(links, users, (email, token) -> mailedTokens.add(token.value())),
                new ResetPassword(
                        links, users,
                        new CreatePasswordHash(hashAlgorithm, PasswordPolicy::withDefaults),
                        new FakeSessionRepository(clock),
                        Duration.ofMinutes(60), clock),
                new SourceThrottle(maxPerWindow, Duration.ofMinutes(15), clock),
                new PassThroughTransactionBoundary());
    }

    private void theOwnerExists() {
        users.save(new User(Email.of(OWNER), hashAlgorithm.hash(PlaintextPassword.of("StrongPassword1!"))));
    }

    @Test
    void the_outcome_tells_a_known_address_from_an_unknown_and_an_unreadable_one() {
        theOwnerExists();

        assertInstanceOf(PasswordResetService.RequestOutcome.LinkSent.class, service.requestLink(OWNER, SOURCE));
        assertInstanceOf(
                PasswordResetService.RequestOutcome.UserNotFound.class,
                service.requestLink("stranger@example.com", SOURCE));
        assertInstanceOf(
                PasswordResetService.RequestOutcome.UnreadableEmail.class,
                service.requestLink("not-an-address", SOURCE));
        assertInstanceOf(
                PasswordResetService.RequestOutcome.UnreadableEmail.class,
                service.requestLink(null, SOURCE));

        assertEquals(1, mailedTokens.size(), "only the known address was mailed");
    }

    @Test
    void a_source_that_spent_its_window_is_throttled_and_nothing_is_mailed() {
        theOwnerExists();
        PasswordResetService throttled = service(1);
        throttled.requestLink(OWNER, SOURCE);

        PasswordResetService.RequestOutcome.Throttled refusal = assertInstanceOf(
                PasswordResetService.RequestOutcome.Throttled.class,
                throttled.requestLink(OWNER, SOURCE));

        assertTrue(refusal.retryAfterSeconds() > 0);
        assertEquals(1, mailedTokens.size());
    }

    @Test
    void the_mailed_token_sets_a_new_password_once() {
        theOwnerExists();
        service.requestLink(OWNER, SOURCE);
        String token = mailedTokens.get(0);

        PasswordResetService.ResetOutcome.PasswordReset reset = assertInstanceOf(
                PasswordResetService.ResetOutcome.PasswordReset.class,
                service.reset(token, "AnotherStrongPassword2!"));
        assertEquals(OWNER, reset.email());

        assertInstanceOf(
                PasswordResetService.ResetOutcome.TokenNotFound.class,
                service.reset(token, "YetAnotherStrongPassword3!"));
    }

    @Test
    void a_weak_password_comes_back_as_broken_rules() {
        theOwnerExists();
        service.requestLink(OWNER, SOURCE);

        PasswordResetService.ResetOutcome.Rejected rejected = assertInstanceOf(
                PasswordResetService.ResetOutcome.Rejected.class,
                service.reset(mailedTokens.get(0), "weak"));

        assertFalse(rejected.passwordErrors().isEmpty());
    }

    @Test
    void a_token_that_cannot_be_constructed_is_unreadable_not_an_exception() {
        assertInstanceOf(
                PasswordResetService.ResetOutcome.UnreadableToken.class,
                service.reset(null, "AnotherStrongPassword2!"));
        assertInstanceOf(
                PasswordResetService.ResetOutcome.UnreadableToken.class,
                service.reset("  ", "AnotherStrongPassword2!"));
    }
}
