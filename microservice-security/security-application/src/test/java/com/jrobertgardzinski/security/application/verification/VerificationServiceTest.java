package com.jrobertgardzinski.security.application.verification;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;
import com.jrobertgardzinski.security.system.verification.VerifyEmail;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is what an
 * unreadable address or token means, and when a source has asked too often.
 */
class VerificationServiceTest {

    private static final String SOURCE = "192.168.0.1";
    private static final String ADDRESS = "user@example.com";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    /** The raw tokens that were "mailed", in order. */
    private final List<String> mailedTokens = new ArrayList<>();

    private final VerificationService service = service(5);

    private VerificationService service(int maxPerWindow) {
        return new VerificationService(
                new RequestEmailVerification(users, (email, token) -> mailedTokens.add(token.value())),
                new VerifyEmail(users, Duration.ofHours(48), clock),
                new SourceThrottle(maxPerWindow, Duration.ofMinutes(15), clock),
                new PassThroughTransactionBoundary());
    }

    @Test
    void the_mailed_token_verifies_the_address_once() {
        assertInstanceOf(VerificationService.RequestOutcome.LinkSent.class, service.requestLink(ADDRESS, SOURCE));
        String token = mailedTokens.get(0);

        VerificationService.VerifyOutcome.Verified verified = assertInstanceOf(
                VerificationService.VerifyOutcome.Verified.class,
                service.verify(token));
        assertEquals(ADDRESS, verified.email());
        assertTrue(users.isVerified(Email.of(ADDRESS)));

        assertInstanceOf(VerificationService.VerifyOutcome.TokenNotFound.class, service.verify(token));
    }

    @Test
    void the_outcome_tells_a_verified_address_from_an_unreadable_one() {
        users.markVerified(Email.of(ADDRESS));

        assertInstanceOf(
                VerificationService.RequestOutcome.AlreadyVerified.class,
                service.requestLink(ADDRESS, SOURCE));
        assertInstanceOf(
                VerificationService.RequestOutcome.UnreadableEmail.class,
                service.requestLink("not-an-address", SOURCE));
        assertInstanceOf(
                VerificationService.RequestOutcome.UnreadableEmail.class,
                service.requestLink(null, SOURCE));

        assertTrue(mailedTokens.isEmpty());
    }

    @Test
    void a_source_that_spent_its_window_is_throttled_and_nothing_is_mailed() {
        VerificationService throttled = service(1);
        throttled.requestLink(ADDRESS, SOURCE);

        assertInstanceOf(
                VerificationService.RequestOutcome.Throttled.class,
                throttled.requestLink("another@example.com", SOURCE));

        assertEquals(1, mailedTokens.size());
    }

    @Test
    void a_token_that_cannot_be_constructed_is_unreadable_not_an_exception() {
        assertInstanceOf(VerificationService.VerifyOutcome.UnreadableToken.class, service.verify(null));
        assertInstanceOf(VerificationService.VerifyOutcome.UnreadableToken.class, service.verify(" "));
    }
}
