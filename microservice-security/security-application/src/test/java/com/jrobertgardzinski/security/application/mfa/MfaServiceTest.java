package com.jrobertgardzinski.security.application.mfa;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.OneShotElevations;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.config.mfa.MfaPolicy;
import com.jrobertgardzinski.security.config.mfa.RecoveryCodeConfig;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.port.CapturingCodeChannel;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.mfa.CodeFactor;
import com.jrobertgardzinski.security.system.mfa.ConfirmEnrolment;
import com.jrobertgardzinski.security.system.mfa.EnrolFactor;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.FakeEnrolmentChallengeStore;
import com.jrobertgardzinski.security.system.mfa.GenerateRecoveryCodes;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;
import com.jrobertgardzinski.security.system.mfa.TotpFactor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is the order of
 * the gates in front of a change to the caller's factors, and that a request which could never
 * succeed does not cost the one-shot elevation.
 */
class MfaServiceTest {

    private static final String OWNER = "owner@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String ACCESS_TOKEN = "the-access-token";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final FakeEnrolledFactorRepository enrolledFactors = new FakeEnrolledFactorRepository();
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();
    private final CapturingCodeChannel mailbox = new CapturingCodeChannel(FactorType.EMAIL_CODE);
    private final OneShotElevations elevations = new OneShotElevations();

    // this deployment offers the e-mail code and TOTP; nobody serves SMS
    private final FactorRegistry registry = new FactorRegistry(List.of(
            new CodeFactor(mailbox, raw -> "hash:" + raw, ChallengeCodeConfig.withDefaults(), clock),
            new TotpFactor(clock, "Security", (enrolment, step) -> true)));
    private final FakeEnrolmentChallengeStore enrolmentStore = new FakeEnrolmentChallengeStore();

    private final MfaService service = new MfaService(
            new EnrolFactor(registry, enrolmentStore),
            new ConfirmEnrolment(registry, enrolledFactors, enrolmentStore),
            new GenerateRecoveryCodes(enrolledFactors, raw -> "hash:" + raw, RecoveryCodeConfig.withDefaults()),
            enrolledFactors,
            users,
            registry,
            new MfaCompliance(enrolledFactors, users, MfaPolicy.withDefaults(), Set.of()),
            elevations,
            new PassThroughTransactionBoundary());

    private void theOwnerExists() {
        users.save(new User(Email.of(OWNER), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
    }

    private void theOwnerHasEnrolledTheEmailFactor() {
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ENROL_FACTOR);
        service.startEnrolment(OWNER, ACCESS_TOKEN, "EMAIL_CODE", null);
        assertInstanceOf(
                MfaService.ConfirmEnrolmentOutcome.Enrolled.class,
                service.confirmEnrolment(OWNER, "EMAIL_CODE", mailbox.lastCodeFor(OWNER)));
    }

    @Test
    void the_list_says_what_the_caller_has_and_what_the_deployment_offers() {
        theOwnerExists();
        theOwnerHasEnrolledTheEmailFactor();

        MfaService.Factors factors = service.factors(OWNER);

        assertEquals(List.of("EMAIL_CODE"), factors.have().stream().map(MfaService.Factor::type).toList());
        assertEquals(List.of("EMAIL_CODE", "TOTP"), factors.offered());
    }

    @Test
    void enrolling_needs_a_fresh_elevation_and_spends_it() {
        theOwnerExists();

        MfaService.StartEnrolmentOutcome.StepUpRequired refused = assertInstanceOf(
                MfaService.StartEnrolmentOutcome.StepUpRequired.class,
                service.startEnrolment(OWNER, ACCESS_TOKEN, "EMAIL_CODE", null));
        assertEquals("enrol-factor", refused.action());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ENROL_FACTOR);
        assertInstanceOf(
                MfaService.StartEnrolmentOutcome.CodeSent.class,
                service.startEnrolment(OWNER, ACCESS_TOKEN, "EMAIL_CODE", null));

        // one-shot: the same token does not open the door twice
        assertInstanceOf(
                MfaService.StartEnrolmentOutcome.StepUpRequired.class,
                service.startEnrolment(OWNER, ACCESS_TOKEN, "EMAIL_CODE", null));
    }

    @Test
    void a_request_without_a_bearer_token_has_not_stepped_up() {
        theOwnerExists();
        theOwnerHasEnrolledTheEmailFactor();

        assertInstanceOf(
                MfaService.StartEnrolmentOutcome.StepUpRequired.class,
                service.startEnrolment(OWNER, null, "EMAIL_CODE", null));
        assertInstanceOf(
                MfaService.RemoveFactorOutcome.StepUpRequired.class,
                service.removeFactor(OWNER, null, "EMAIL_CODE"));
        assertInstanceOf(
                MfaService.GenerateRecoveryCodesOutcome.StepUpRequired.class,
                service.generateRecoveryCodes(OWNER, null));
    }

    @Test
    void the_code_of_the_email_factor_goes_to_the_callers_own_address_whatever_the_request_names() {
        theOwnerExists();
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ENROL_FACTOR);

        service.startEnrolment(OWNER, ACCESS_TOKEN, "EMAIL_CODE", "attacker@evil.com");

        assertNotNull(mailbox.lastCodeFor(OWNER));
        assertNull(mailbox.lastCodeFor("attacker@evil.com"));
    }

    @Test
    void a_possession_factor_answers_with_what_to_show() {
        theOwnerExists();
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ENROL_FACTOR);

        MfaService.StartEnrolmentOutcome.SetupReady ready = assertInstanceOf(
                MfaService.StartEnrolmentOutcome.SetupReady.class,
                service.startEnrolment(OWNER, ACCESS_TOKEN, "TOTP", null));

        assertTrue(ready.display().startsWith("otpauth://"), ready.display());
    }

    @Test
    void a_factor_type_that_cannot_be_enrolled_does_not_cost_the_elevation() {
        theOwnerExists();
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ENROL_FACTOR);

        assertInstanceOf(
                MfaService.StartEnrolmentOutcome.UnsupportedFactor.class,
                service.startEnrolment(OWNER, ACCESS_TOKEN, "SMS_CODE", null));
        assertInstanceOf(
                MfaService.StartEnrolmentOutcome.UnreadableFactorType.class,
                service.startEnrolment(OWNER, ACCESS_TOKEN, "  ", null));

        // the elevation is still there for the corrected attempt
        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.ENROL_FACTOR));
    }

    @Test
    void only_the_right_code_enrols_and_only_what_was_started() {
        theOwnerExists();

        assertInstanceOf(
                MfaService.ConfirmEnrolmentOutcome.NoPendingEnrolment.class,
                service.confirmEnrolment(OWNER, "EMAIL_CODE", "000000"));

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ENROL_FACTOR);
        service.startEnrolment(OWNER, ACCESS_TOKEN, "EMAIL_CODE", null);

        assertInstanceOf(
                MfaService.ConfirmEnrolmentOutcome.WrongProof.class,
                service.confirmEnrolment(OWNER, "EMAIL_CODE", "not-the-code"));
        assertInstanceOf(
                MfaService.ConfirmEnrolmentOutcome.UnreadableFactorType.class,
                service.confirmEnrolment(OWNER, " ", mailbox.lastCodeFor(OWNER)));

        MfaService.ConfirmEnrolmentOutcome.Enrolled enrolled = assertInstanceOf(
                MfaService.ConfirmEnrolmentOutcome.Enrolled.class,
                service.confirmEnrolment(OWNER, "EMAIL_CODE", mailbox.lastCodeFor(OWNER)));
        assertEquals("EMAIL_CODE", enrolled.type());
    }

    @Test
    void removal_needs_a_fresh_elevation_and_then_removes() {
        theOwnerExists();
        theOwnerHasEnrolledTheEmailFactor();

        MfaService.RemoveFactorOutcome.StepUpRequired refused = assertInstanceOf(
                MfaService.RemoveFactorOutcome.StepUpRequired.class,
                service.removeFactor(OWNER, ACCESS_TOKEN, "EMAIL_CODE"));
        assertEquals("remove-factor", refused.action());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.REMOVE_FACTOR);
        assertInstanceOf(
                MfaService.RemoveFactorOutcome.Removed.class,
                service.removeFactor(OWNER, ACCESS_TOKEN, "EMAIL_CODE"));

        assertEquals(List.of(), service.factors(OWNER).have());
    }

    @Test
    void a_removal_that_would_break_the_role_floor_is_refused_before_the_elevation_is_spent() {
        theOwnerExists();
        theOwnerHasEnrolledTheEmailFactor();
        // a MODERATOR owes two factors: the password and the one enrolled are exactly that
        users.setRoles(Email.of(OWNER), Set.of(Role.MODERATOR));
        elevations.elevate(ACCESS_TOKEN, StepUpAction.REMOVE_FACTOR);

        assertInstanceOf(
                MfaService.RemoveFactorOutcome.WouldBreakFloor.class,
                service.removeFactor(OWNER, ACCESS_TOKEN, "EMAIL_CODE"));

        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.REMOVE_FACTOR));
        assertEquals(1, service.factors(OWNER).have().size());
    }

    @Test
    void a_blank_factor_type_is_not_removed_and_does_not_cost_the_elevation() {
        theOwnerExists();
        elevations.elevate(ACCESS_TOKEN, StepUpAction.REMOVE_FACTOR);

        assertInstanceOf(
                MfaService.RemoveFactorOutcome.UnreadableFactorType.class,
                service.removeFactor(OWNER, ACCESS_TOKEN, " "));

        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.REMOVE_FACTOR));
    }

    @Test
    void recovery_codes_need_a_fresh_elevation_and_are_counted_afterwards() {
        theOwnerExists();

        MfaService.GenerateRecoveryCodesOutcome.StepUpRequired refused = assertInstanceOf(
                MfaService.GenerateRecoveryCodesOutcome.StepUpRequired.class,
                service.generateRecoveryCodes(OWNER, ACCESS_TOKEN));
        assertEquals("generate-recovery-codes", refused.action());
        assertEquals(0, service.unusedRecoveryCodes(OWNER));

        elevations.elevate(ACCESS_TOKEN, StepUpAction.GENERATE_RECOVERY_CODES);
        MfaService.GenerateRecoveryCodesOutcome.Generated generated = assertInstanceOf(
                MfaService.GenerateRecoveryCodesOutcome.Generated.class,
                service.generateRecoveryCodes(OWNER, ACCESS_TOKEN));

        assertEquals(RecoveryCodeConfig.withDefaults().count().value(), generated.codes().size());
        assertEquals(generated.codes().size(), service.unusedRecoveryCodes(OWNER));
    }
}
