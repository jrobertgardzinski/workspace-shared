package com.jrobertgardzinski.security.application.mfa;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.OneShotElevations;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.config.mfa.StepUpPolicy;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.port.CapturingCodeChannel;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.mfa.CodeFactor;
import com.jrobertgardzinski.security.system.mfa.ContinueStepUp;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.MfaChain;
import com.jrobertgardzinski.security.system.mfa.PresentProof;
import com.jrobertgardzinski.security.system.mfa.StepUp;
import com.jrobertgardzinski.security.system.mfa.StepUpStore;
import com.jrobertgardzinski.security.system.password.VerifyPassword;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is whose window
 * an attempt is counted in, and that somebody who has just proved themselves owes it nothing.
 */
class StepUpServiceTest {

    private static final String OWNER = "owner@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String ACCESS_TOKEN = "the-access-token";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final FakeEnrolledFactorRepository enrolledFactors = new FakeEnrolledFactorRepository();
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();
    private final CapturingCodeChannel mailbox = new CapturingCodeChannel(FactorType.EMAIL_CODE);
    private final OneShotElevations elevations = new OneShotElevations();
    private final Tickets tickets = new Tickets();

    private final StepUpService service = service(30);

    /** Step-ups in flight, without the deadline the real store keeps. */
    private static final class Tickets implements StepUpStore {

        private final Map<String, StepUpPending> byTicket = new HashMap<>();

        @Override
        public String open(StepUpPending pending) {
            String ticket = UUID.randomUUID().toString();
            byTicket.put(ticket, pending);
            return ticket;
        }

        @Override
        public Optional<StepUpPending> find(String ticket) {
            return Optional.ofNullable(byTicket.get(ticket));
        }

        @Override
        public void replace(String ticket, StepUpPending pending) {
            byTicket.put(ticket, pending);
        }

        @Override
        public void close(String ticket) {
            byTicket.remove(ticket);
        }
    }

    private StepUpService service(int maxPerWindow) {
        MfaChain mfaChain = new MfaChain(
                new FactorRegistry(List.of(
                        new CodeFactor(mailbox, raw -> "hash:" + raw, ChallengeCodeConfig.withDefaults(), clock))),
                ChallengeCodeConfig.withDefaults(),
                enrolledFactors,
                raw -> "hash:" + raw, clock, 10);
        return new StepUpService(
                new StepUp(
                        StepUpPolicy.withDefaults(),
                        new VerifyPassword(users, hashAlgorithm),
                        users, enrolledFactors, mfaChain, tickets, elevations),
                new ContinueStepUp(
                        new PresentProof(mfaChain, tickets.chains(), clock),
                        tickets, elevations),
                new SourceThrottle(maxPerWindow, Duration.ofMinutes(15), clock));
    }

    private void anAccountOf(String email) {
        users.save(new User(Email.of(email), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
    }

    private void theOwnerHasTheEmailFactor() {
        enrolledFactors.enrol(new EnrolledFactor(Email.of(OWNER), FactorType.EMAIL_CODE, "E-mail code", 0, OWNER));
    }

    @Test
    void the_right_password_elevates_the_token_for_the_action_named() {
        anAccountOf(OWNER);

        assertInstanceOf(
                StepUpService.StartOutcome.Elevated.class,
                service.start(OWNER, ACCESS_TOKEN, "delete-account", PASSWORD));

        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT));
        assertFalse(elevations.holds(ACCESS_TOKEN, StepUpAction.CHANGE_EMAIL));
    }

    @Test
    void a_wrong_a_missing_and_a_blank_password_are_one_answer() {
        anAccountOf(OWNER);

        for (String password : new String[] {"WrongButStrongPassword1!", null, "   "}) {
            assertInstanceOf(
                    StepUpService.StartOutcome.WrongPassword.class,
                    service.start(OWNER, ACCESS_TOKEN, "delete-account", password));
        }

        assertFalse(elevations.holds(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT));
    }

    @Test
    void an_action_outside_the_catalogue_mints_nothing() {
        anAccountOf(OWNER);

        assertInstanceOf(
                StepUpService.StartOutcome.UnknownAction.class,
                service.start(OWNER, ACCESS_TOKEN, "make-me-admin", PASSWORD));
        assertInstanceOf(
                StepUpService.StartOutcome.UnknownAction.class,
                service.start(OWNER, ACCESS_TOKEN, null, PASSWORD));
    }

    @Test
    void an_account_gone_from_under_a_live_token_is_not_a_wrong_password() {
        assertInstanceOf(
                StepUpService.StartOutcome.AccountGone.class,
                service.start("gone@example.com", ACCESS_TOKEN, "delete-account", PASSWORD));
    }

    @Test
    void an_account_with_nothing_to_prove_with_is_told_so() {
        anAccountOf(OWNER);
        users.setPasswordless(Email.of(OWNER), true);

        assertInstanceOf(
                StepUpService.StartOutcome.NothingToProveWith.class,
                service.start(OWNER, ACCESS_TOKEN, "delete-account", null));
    }

    @Test
    void an_enrolled_factor_is_walked_after_the_password_and_only_then_elevates() {
        anAccountOf(OWNER);
        theOwnerHasTheEmailFactor();

        StepUpService.StartOutcome.FactorRequired factorRequired = assertInstanceOf(
                StepUpService.StartOutcome.FactorRequired.class,
                service.start(OWNER, ACCESS_TOKEN, "delete-account", PASSWORD));
        assertEquals("EMAIL_CODE", factorRequired.nextFactor());
        assertFalse(elevations.holds(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT));

        StepUpService.FactorOutcome.WrongProof wrongProof = assertInstanceOf(
                StepUpService.FactorOutcome.WrongProof.class,
                service.presentFactor(OWNER, factorRequired.ticket(), "not-the-code"));
        assertEquals(ChallengeCodeConfig.withDefaults().maxAttempts().value() - 1, wrongProof.attemptsLeft());

        assertInstanceOf(
                StepUpService.FactorOutcome.Elevated.class,
                service.presentFactor(OWNER, factorRequired.ticket(), mailbox.lastCodeFor(OWNER)));
        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT));
    }

    @Test
    void a_missing_ticket_or_proof_and_a_ticket_nobody_opened_are_told_apart() {
        anAccountOf(OWNER);

        assertInstanceOf(
                StepUpService.FactorOutcome.MissingTicketOrProof.class,
                service.presentFactor(OWNER, null, "000000"));
        assertInstanceOf(
                StepUpService.FactorOutcome.MissingTicketOrProof.class,
                service.presentFactor(OWNER, "a-ticket", "  "));
        assertInstanceOf(
                StepUpService.FactorOutcome.InvalidTicket.class,
                service.presentFactor(OWNER, "a-ticket-nobody-opened", "000000"));
    }

    @Test
    void the_window_is_the_callers_own_and_both_steps_spend_it() {
        anAccountOf(OWNER);
        anAccountOf("colleague@example.com");
        StepUpService throttled = service(1);

        throttled.start(OWNER, ACCESS_TOKEN, "delete-account", "WrongButStrongPassword1!");

        // the right password is not even looked at, on either step
        StepUpService.StartOutcome.Throttled refusal = assertInstanceOf(
                StepUpService.StartOutcome.Throttled.class,
                throttled.start(OWNER, ACCESS_TOKEN, "delete-account", PASSWORD));
        assertTrue(refusal.retryAfterSeconds() > 0);
        assertInstanceOf(
                StepUpService.FactorOutcome.Throttled.class,
                throttled.presentFactor(OWNER, "a-ticket", "000000"));

        // somebody else's spent window does not answer for this caller
        assertInstanceOf(
                StepUpService.StartOutcome.Elevated.class,
                throttled.start("colleague@example.com", "another-token", "delete-account", PASSWORD));
    }

    @Test
    void proving_yourself_forgives_the_window() {
        anAccountOf(OWNER);
        StepUpService twoPerWindow = service(2);

        twoPerWindow.start(OWNER, ACCESS_TOKEN, "delete-account", "WrongButStrongPassword1!");
        assertInstanceOf(
                StepUpService.StartOutcome.Elevated.class,
                twoPerWindow.start(OWNER, ACCESS_TOKEN, "delete-account", PASSWORD));

        // a third attempt in the same window: charged, it would be refused before it is judged
        assertInstanceOf(
                StepUpService.StartOutcome.WrongPassword.class,
                twoPerWindow.start(OWNER, ACCESS_TOKEN, "delete-account", "WrongButStrongPassword1!"));
    }
}
