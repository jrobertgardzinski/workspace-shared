package com.jrobertgardzinski.security.application.authentication;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.repository.FakeAuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeRejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.repository.FakeSessionRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.system.authentication.AuthenticationFactory;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.FakePendingAuthenticationStore;
import com.jrobertgardzinski.security.system.mfa.MfaChain;
import com.jrobertgardzinski.security.system.mfa.OpenMfaChain;
import com.jrobertgardzinski.security.system.password.VerifyPassword;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is what an
 * unreadable credential means, and when a source has asked too often.
 */
class AuthenticationServiceTest {

    private static final String SOURCE = "192.168.0.1";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final int MAX_FAILURES = 3;
    private static final int BLOCK_MINUTES = 5;

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeRejectedAuthenticationRepository rejections = new FakeRejectedAuthenticationRepository();
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();

    private final AuthenticationService service = service(30);

    private AuthenticationService service(int maxPerWindow) {
        // no factors are enrolled here, so the chain is empty and sign-in is single-factor
        FakeEnrolledFactorRepository enrolledFactors = new FakeEnrolledFactorRepository();
        MfaChain mfaChain = new MfaChain(
                new FactorRegistry(List.of()),
                ChallengeCodeConfig.withDefaults(),
                enrolledFactors,
                raw -> "hash:" + raw, clock, 10);
        FakePendingAuthenticationStore pendingStore = new FakePendingAuthenticationStore();
        return new AuthenticationService(
                AuthenticationFactory.assemble(
                        users, rejections, new FakeAuthenticationBlockRepository(),
                        new FakeSessionRepository(clock),
                        new VerifyPassword(users, hashAlgorithm),
                        BruteForceConfig.builder()
                                .maxFailures(MAX_FAILURES)
                                .failureWindowMinutes(15)
                                .minBlockMinutes(BLOCK_MINUTES)
                                .maxBlockMinutes(BLOCK_MINUTES)
                                .build(),
                        new SessionTokensConfig(new RefreshTokenValidityInHours(24), new AccessTokenValidityInHours(1)),
                        clock,
                        () -> BLOCK_MINUTES,
                        AccessTokenMint.RANDOM,
                        enrolledFactors,
                        mfaChain,
                        pendingStore,
                        new OpenMfaChain(mfaChain, pendingStore)
                ).authentication(),
                new SourceThrottle(maxPerWindow, Duration.ofMinutes(15), clock),
                new PassThroughTransactionBoundary());
    }

    private void aVerifiedUser() {
        anUnverifiedUser();
        users.markVerified(Email.of(EMAIL));
    }

    private void anUnverifiedUser() {
        users.save(new User(Email.of(EMAIL), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
    }

    @Test
    void the_right_credentials_answer_with_both_tokens_as_strings() {
        aVerifiedUser();

        AuthenticationService.Outcome outcome = service.authenticate(EMAIL, PASSWORD, SOURCE, "a browser");

        AuthenticationService.Outcome.Authenticated authenticated =
                assertInstanceOf(AuthenticationService.Outcome.Authenticated.class, outcome);
        assertFalse(authenticated.accessToken().isBlank());
        assertFalse(authenticated.refreshToken().isBlank());
    }

    @Test
    void a_wrong_password_is_rejected() {
        aVerifiedUser();

        AuthenticationService.Outcome outcome = service.authenticate(EMAIL, "WrongButStrongPassword1!", SOURCE, "");

        assertInstanceOf(AuthenticationService.Outcome.Rejected.class, outcome);
    }

    @Test
    void an_address_the_domain_cannot_construct_is_unreadable_and_costs_no_failure() {
        aVerifiedUser();

        for (int attempt = 0; attempt < MAX_FAILURES + 1; attempt++) {
            assertInstanceOf(
                    AuthenticationService.Outcome.UnreadableCredentials.class,
                    service.authenticate("not-an-address", PASSWORD, SOURCE, ""));
        }

        // nothing was charged: the real account still signs in from the same source
        assertInstanceOf(
                AuthenticationService.Outcome.Authenticated.class,
                service.authenticate(EMAIL, PASSWORD, SOURCE, ""));
    }

    @Test
    void a_missing_address_or_password_is_unreadable_not_an_exception() {
        assertInstanceOf(
                AuthenticationService.Outcome.UnreadableCredentials.class,
                service.authenticate(null, PASSWORD, SOURCE, ""));
        assertInstanceOf(
                AuthenticationService.Outcome.UnreadableCredentials.class,
                service.authenticate(EMAIL, null, SOURCE, ""));
    }

    @Test
    void the_right_credentials_of_an_unverified_address_are_not_a_session() {
        anUnverifiedUser();

        AuthenticationService.Outcome outcome = service.authenticate(EMAIL, PASSWORD, SOURCE, "");

        assertInstanceOf(AuthenticationService.Outcome.EmailNotVerified.class, outcome);
    }

    @Test
    void the_failure_limit_blocks_and_says_until_when() {
        aVerifiedUser();
        for (int attempt = 0; attempt < MAX_FAILURES; attempt++) {
            service.authenticate(EMAIL, "WrongButStrongPassword1!", SOURCE, "");
        }

        AuthenticationService.Outcome outcome = service.authenticate(EMAIL, PASSWORD, SOURCE, "");

        AuthenticationService.Outcome.Blocked blocked =
                assertInstanceOf(AuthenticationService.Outcome.Blocked.class, outcome);
        assertEquals(LocalDateTime.now(clock).plusMinutes(BLOCK_MINUTES), blocked.until());
    }

    @Test
    void a_source_that_spent_its_window_is_throttled_before_any_credential_is_looked_at() {
        aVerifiedUser();
        AuthenticationService throttled = service(1);
        throttled.authenticate(EMAIL, PASSWORD, SOURCE, "");

        AuthenticationService.Outcome outcome = throttled.authenticate(EMAIL, PASSWORD, SOURCE, "");

        AuthenticationService.Outcome.Throttled refusal =
                assertInstanceOf(AuthenticationService.Outcome.Throttled.class, outcome);
        assertTrue(refusal.retryAfterSeconds() > 0);
    }
}
