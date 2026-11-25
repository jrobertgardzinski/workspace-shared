package com.jrobertgardzinski.security.application.federation;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.port.CapturingCodeChannel;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeSessionRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.AccessTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.RefreshTokenValidityInHours;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.system.federation.FederatedSignIn;
import com.jrobertgardzinski.security.system.mfa.CodeFactor;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.FakePendingAuthenticationStore;
import com.jrobertgardzinski.security.system.mfa.MfaChain;
import com.jrobertgardzinski.security.system.mfa.OpenMfaChain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes what a provider asserted as strings and answers in plain values; what it decides
 * on its own is what an address the domain cannot construct means: the provider's mistake, and no
 * account.
 */
class FederationServiceTest {

    private static final String SOURCE = "203.0.113.9";
    private static final String EMAIL = "newcomer@example.com";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final FakeEnrolledFactorRepository enrolledFactors = new FakeEnrolledFactorRepository();
    private final CapturingCodeChannel mailbox = new CapturingCodeChannel(FactorType.EMAIL_CODE);

    private final MfaChain mfaChain = new MfaChain(
            new FactorRegistry(List.of(
                    new CodeFactor(mailbox, raw -> "hash:" + raw, ChallengeCodeConfig.withDefaults(), clock))),
            ChallengeCodeConfig.withDefaults(),
            enrolledFactors,
            raw -> "hash:" + raw, clock, 10);

    private final FederationService service = new FederationService(
            new FederatedSignIn(
                    users,
                    new FakeSessionRepository(clock),
                    new FakeHashAlgorithm(),
                    new SessionTokensConfig(new RefreshTokenValidityInHours(24), new AccessTokenValidityInHours(1)),
                    clock,
                    AccessTokenMint.RANDOM,
                    enrolledFactors,
                    new OpenMfaChain(mfaChain, new FakePendingAuthenticationStore())),
            new PassThroughTransactionBoundary());

    private FederationService.SignInOutcome signIn(String email, boolean vouched) {
        return service.signIn("stub", "sub-" + email, email, vouched, SOURCE, "a browser");
    }

    @Test
    void a_vouched_identity_nobody_has_seen_becomes_an_account_and_a_session() {
        FederationService.SignInOutcome.SignedIn signedIn = assertInstanceOf(
                FederationService.SignInOutcome.SignedIn.class,
                signIn(EMAIL, true));

        assertFalse(signedIn.accessToken().isBlank());
        assertFalse(signedIn.refreshToken().isBlank());
        assertTrue(users.findBy(Email.of(EMAIL)).isPresent());
    }

    @Test
    void an_address_the_provider_does_not_vouch_for_proves_nothing() {
        assertInstanceOf(
                FederationService.SignInOutcome.EmailNotVouched.class,
                signIn(EMAIL, false));

        assertTrue(users.findBy(Email.of(EMAIL)).isEmpty());
    }

    @Test
    void what_the_provider_calls_an_address_and_is_not_one_is_unreadable_not_an_exception() {
        assertInstanceOf(
                FederationService.SignInOutcome.UnreadableEmail.class,
                signIn("somebody@dotless", true));
    }

    @Test
    void an_account_being_deleted_does_not_sign_in_through_a_provider_either() {
        signIn(EMAIL, true);
        users.markPendingDeletion(Email.of(EMAIL));

        assertInstanceOf(
                FederationService.SignInOutcome.AccountClosing.class,
                signIn(EMAIL, true));
    }

    @Test
    void an_account_with_an_enrolled_factor_gets_a_ticket_not_a_session() {
        signIn(EMAIL, true);
        enrolledFactors.enrol(new EnrolledFactor(Email.of(EMAIL), FactorType.EMAIL_CODE, "E-mail code", 0, EMAIL));

        FederationService.SignInOutcome.MfaRequired mfaRequired = assertInstanceOf(
                FederationService.SignInOutcome.MfaRequired.class,
                signIn(EMAIL, true));

        assertEquals("EMAIL_CODE", mfaRequired.nextFactor());
        assertFalse(mfaRequired.ticket().isBlank());
    }
}
