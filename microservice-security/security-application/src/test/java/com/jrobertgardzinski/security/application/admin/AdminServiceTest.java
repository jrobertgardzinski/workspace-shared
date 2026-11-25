package com.jrobertgardzinski.security.application.admin;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.OneShotElevations;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.roles.BootstrapAdmins;
import com.jrobertgardzinski.security.system.roles.RequireRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is the order of
 * the two gates in front of every lever: an ADMIN first, a fresh step-up last, and what the request
 * names read in between.
 */
class AdminServiceTest {

    private static final String ADMIN = "admin@example.com";
    private static final String SOMEBODY = "somebody@example.com";
    private static final String LOCKED_OUT = "locked-out@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String ACCESS_TOKEN = "the-access-token";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final FakeEnrolledFactorRepository enrolledFactors = new FakeEnrolledFactorRepository();
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();
    private final OneShotElevations elevations = new OneShotElevations();

    private final AdminService service = new AdminService(
            new RequireRole(
                    BootstrapAdmins.of(List.of(ADMIN)),
                    email -> users.findBy(email).map(User::roles).orElse(Set.of())),
            enrolledFactors,
            elevations,
            new PassThroughTransactionBoundary());

    private void anAccountOf(String email) {
        users.save(new User(Email.of(email), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
    }

    private void theLockedOutUserHasAFactor() {
        anAccountOf(LOCKED_OUT);
        enrolledFactors.enrol(
                new EnrolledFactor(Email.of(LOCKED_OUT), FactorType.EMAIL_CODE, "E-mail code", 0, LOCKED_OUT));
    }

    @Test
    void somebody_who_is_not_an_admin_learns_only_that() {
        anAccountOf(SOMEBODY);
        theLockedOutUserHasAFactor();
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_RESET);

        assertInstanceOf(
                AdminService.ResetFactorsOutcome.NotAnAdmin.class,
                service.resetFactors(SOMEBODY, ACCESS_TOKEN, LOCKED_OUT));
        // not even that the address they named is no address
        assertInstanceOf(
                AdminService.ResetFactorsOutcome.NotAnAdmin.class,
                service.resetFactors(SOMEBODY, ACCESS_TOKEN, "not-an-address"));

        assertEquals(1, enrolledFactors.findByUser(Email.of(LOCKED_OUT)).size());
        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.ADMIN_RESET));
    }

    @Test
    void an_admin_must_have_just_stepped_up_and_the_elevation_is_spent_on_the_reset() {
        anAccountOf(ADMIN);
        theLockedOutUserHasAFactor();

        AdminService.ResetFactorsOutcome.StepUpRequired refused = assertInstanceOf(
                AdminService.ResetFactorsOutcome.StepUpRequired.class,
                service.resetFactors(ADMIN, ACCESS_TOKEN, LOCKED_OUT));
        assertEquals("admin-reset", refused.action());
        assertEquals(1, enrolledFactors.findByUser(Email.of(LOCKED_OUT)).size());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_RESET);
        AdminService.ResetFactorsOutcome.FactorsReset reset = assertInstanceOf(
                AdminService.ResetFactorsOutcome.FactorsReset.class,
                service.resetFactors(ADMIN, ACCESS_TOKEN, LOCKED_OUT));
        assertEquals(LOCKED_OUT, reset.user());
        assertEquals(List.of(), enrolledFactors.findByUser(Email.of(LOCKED_OUT)));

        // one-shot: the same token does not pull the lever twice
        assertInstanceOf(
                AdminService.ResetFactorsOutcome.StepUpRequired.class,
                service.resetFactors(ADMIN, ACCESS_TOKEN, LOCKED_OUT));
    }

    @Test
    void an_admin_without_a_bearer_token_has_not_stepped_up() {
        anAccountOf(ADMIN);

        assertInstanceOf(
                AdminService.ResetFactorsOutcome.StepUpRequired.class,
                service.resetFactors(ADMIN, null, LOCKED_OUT));
    }

    @Test
    void a_typo_in_the_address_does_not_cost_the_elevation() {
        anAccountOf(ADMIN);
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_RESET);

        assertInstanceOf(
                AdminService.ResetFactorsOutcome.InvalidEmail.class,
                service.resetFactors(ADMIN, ACCESS_TOKEN, "not-an-address"));

        // the elevation is still there for the corrected attempt
        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.ADMIN_RESET));
    }
}
