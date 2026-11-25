package com.jrobertgardzinski.security.application.admin;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.config.Configuration;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.password.config.RequiresDigit;
import com.jrobertgardzinski.password.config.SpecialChars;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.OneShotElevations;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.roles.BootstrapAdmins;
import com.jrobertgardzinski.security.system.roles.RequireRole;
import com.jrobertgardzinski.security.system.roles.SetUserRoles;
import com.jrobertgardzinski.security.system.settings.SetSetting;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is the order of
 * the two gates in front of every lever: an ADMIN first, a fresh step-up last, and what the request
 * names read in between, so that a typo does not cost the one-shot elevation.
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

    /** The live level of the ladder: what an ADMIN has told the rules so far. */
    private final Map<String, String> rows = new HashMap<>();
    private final Configuration configuration = new Configuration(rows::get, key -> null);

    private final AdminService service = service(BootstrapAdmins.of(List.of(ADMIN)));

    private AdminService service(BootstrapAdmins bootstrapAdmins) {
        return new AdminService(
                new RequireRole(
                        bootstrapAdmins,
                        email -> users.findBy(email).map(User::roles).orElse(Set.of())),
                new SetUserRoles(users, bootstrapAdmins),
                new SetSetting(configuration::liveKey, rows::put),
                configuration,
                enrolledFactors,
                elevations,
                new PassThroughTransactionBoundary());
    }

    private void anAccountOf(String email) {
        users.save(new User(Email.of(email), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
    }

    private void theLockedOutUserHasAFactor() {
        anAccountOf(LOCKED_OUT);
        enrolledFactors.enrol(
                new EnrolledFactor(Email.of(LOCKED_OUT), FactorType.EMAIL_CODE, "E-mail code", 0, LOCKED_OUT));
    }

    private void theLengthAndTheDigitRuleAreLive() {
        configuration.liveOver(MinLength.DEFAULT);
        configuration.liveOver(RequiresDigit.DEFAULT);
    }

    // ---- resetting somebody's factors ----

    @Test
    void somebody_who_is_not_an_admin_learns_only_that() {
        anAccountOf(SOMEBODY);
        theLockedOutUserHasAFactor();
        theLengthAndTheDigitRuleAreLive();
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_RESET);

        assertInstanceOf(
                AdminService.ResetFactorsOutcome.NotAnAdmin.class,
                service.resetFactors(SOMEBODY, ACCESS_TOKEN, LOCKED_OUT));
        // not even that the address they named is no address, or that the role they named is no role
        assertInstanceOf(
                AdminService.ResetFactorsOutcome.NotAnAdmin.class,
                service.resetFactors(SOMEBODY, ACCESS_TOKEN, "not-an-address"));
        assertInstanceOf(
                AdminService.SetRolesOutcome.NotAnAdmin.class,
                service.setRoles(SOMEBODY, ACCESS_TOKEN, "not-an-address", List.of("EMPEROR")));
        assertInstanceOf(
                AdminService.SettingsOutcome.NotAnAdmin.class,
                service.settings(SOMEBODY));
        assertInstanceOf(
                AdminService.SettingsOutcome.NotAnAdmin.class,
                service.passwordPolicy(SOMEBODY));
        assertInstanceOf(
                AdminService.SetSettingOutcome.NotAnAdmin.class,
                service.setSetting(SOMEBODY, ACCESS_TOKEN, MinLength.KEY, null));
        assertInstanceOf(
                AdminService.SetPasswordMinLengthOutcome.NotAnAdmin.class,
                service.setPasswordMinLength(SOMEBODY, ACCESS_TOKEN, "not-a-number"));

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
        anAccountOf(SOMEBODY);
        theLengthAndTheDigitRuleAreLive();

        assertInstanceOf(
                AdminService.ResetFactorsOutcome.StepUpRequired.class,
                service.resetFactors(ADMIN, null, LOCKED_OUT));
        assertInstanceOf(
                AdminService.SetRolesOutcome.StepUpRequired.class,
                service.setRoles(ADMIN, null, SOMEBODY, List.of("MODERATOR")));
        assertInstanceOf(
                AdminService.SetSettingOutcome.StepUpRequired.class,
                service.setSetting(ADMIN, null, MinLength.KEY, "12"));
        assertInstanceOf(
                AdminService.SetPasswordMinLengthOutcome.StepUpRequired.class,
                service.setPasswordMinLength(ADMIN, null, "12"));
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

    // ---- granting and revoking roles ----

    @Test
    void a_grant_needs_a_fresh_elevation_and_answers_with_the_roles_now_held() {
        anAccountOf(ADMIN);
        anAccountOf(SOMEBODY);

        AdminService.SetRolesOutcome.StepUpRequired refused = assertInstanceOf(
                AdminService.SetRolesOutcome.StepUpRequired.class,
                service.setRoles(ADMIN, ACCESS_TOKEN, SOMEBODY, List.of("MODERATOR")));
        assertEquals("admin-roles", refused.action());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_ROLES);
        AdminService.SetRolesOutcome.Updated updated = assertInstanceOf(
                AdminService.SetRolesOutcome.Updated.class,
                service.setRoles(ADMIN, ACCESS_TOKEN, SOMEBODY, List.of(" moderator ")));

        assertEquals(SOMEBODY, updated.user());
        // sorted, and USER is what "signed in" means: it cannot be taken away
        assertEquals(List.of("MODERATOR", "USER"), updated.roles());
        assertFalse(elevations.holds(ACCESS_TOKEN, StepUpAction.ADMIN_ROLES));
    }

    @Test
    void an_address_or_a_role_that_does_not_exist_does_not_cost_the_elevation() {
        anAccountOf(ADMIN);
        anAccountOf(SOMEBODY);
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_ROLES);

        assertInstanceOf(
                AdminService.SetRolesOutcome.InvalidEmail.class,
                service.setRoles(ADMIN, ACCESS_TOKEN, "not-an-address", List.of("MODERATOR")));

        AdminService.SetRolesOutcome.UnknownRole unknownRole = assertInstanceOf(
                AdminService.SetRolesOutcome.UnknownRole.class,
                service.setRoles(ADMIN, ACCESS_TOKEN, SOMEBODY, List.of("EMPEROR")));
        assertEquals(List.of("ADMIN", "MODERATOR", "USER"), unknownRole.known());

        // a request that carried no list at all names no role either
        assertInstanceOf(
                AdminService.SetRolesOutcome.UnknownRole.class,
                service.setRoles(ADMIN, ACCESS_TOKEN, SOMEBODY, null));

        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.ADMIN_ROLES));
    }

    @Test
    void an_address_nobody_holds_is_said_by_the_use_case() {
        anAccountOf(ADMIN);
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_ROLES);

        assertInstanceOf(
                AdminService.SetRolesOutcome.NoSuchUser.class,
                service.setRoles(ADMIN, ACCESS_TOKEN, "nobody@example.com", List.of("MODERATOR")));
    }

    @Test
    void the_last_admin_cannot_drop_their_own_grant() {
        // no bootstrap admin in this deployment: the only ADMIN there is holds it by a row
        AdminService withoutBootstrap = service(BootstrapAdmins.none());
        anAccountOf(ADMIN);
        users.setRoles(Email.of(ADMIN), Set.of(Role.ADMIN));
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_ROLES);

        AdminService.SetRolesOutcome.WouldLeaveNoAdmin refused = assertInstanceOf(
                AdminService.SetRolesOutcome.WouldLeaveNoAdmin.class,
                withoutBootstrap.setRoles(ADMIN, ACCESS_TOKEN, ADMIN, List.of("USER")));

        assertEquals(List.of("ADMIN", "USER"), refused.roles());
    }

    // ---- the rules the running system may be told ----

    @Test
    void reading_the_rules_needs_the_role_and_no_step_up() {
        anAccountOf(ADMIN);
        theLengthAndTheDigitRuleAreLive();

        AdminService.SettingsOutcome.Settings settings = assertInstanceOf(
                AdminService.SettingsOutcome.Settings.class,
                service.settings(ADMIN));

        // in the order the rules were declared
        assertEquals(
                List.of(MinLength.KEY, RequiresDigit.KEY),
                settings.settings().stream().map(AdminService.Setting::key).toList());
        assertEquals(MinLength.DEFAULT.value(), settings.settings().get(0).value());
    }

    @Test
    void the_password_policy_is_reported_in_its_own_order_whatever_else_is_live() {
        anAccountOf(ADMIN);
        configuration.liveOver(RequiresDigit.DEFAULT);
        configuration.liveOver(SpecialChars.DEFAULT);
        configuration.liveOver(MinLength.DEFAULT);

        AdminService.SettingsOutcome.Settings policy = assertInstanceOf(
                AdminService.SettingsOutcome.Settings.class,
                service.passwordPolicy(ADMIN));

        assertEquals(
                List.of(MinLength.KEY, SpecialChars.KEY, RequiresDigit.KEY),
                policy.settings().stream().map(AdminService.Setting::key).toList());
    }

    @Test
    void telling_a_rule_needs_a_fresh_elevation_and_the_rule_holds_what_it_was_told() {
        anAccountOf(ADMIN);
        theLengthAndTheDigitRuleAreLive();

        AdminService.SetSettingOutcome.StepUpRequired refused = assertInstanceOf(
                AdminService.SetSettingOutcome.StepUpRequired.class,
                service.setSetting(ADMIN, ACCESS_TOKEN, MinLength.KEY, "12"));
        assertEquals("admin-settings", refused.action());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_SETTINGS);
        AdminService.SetSettingOutcome.Accepted accepted = assertInstanceOf(
                AdminService.SetSettingOutcome.Accepted.class,
                service.setSetting(ADMIN, ACCESS_TOKEN, MinLength.KEY, "12"));
        assertEquals(12, accepted.value());

        AdminService.SettingsOutcome.Settings settings = assertInstanceOf(
                AdminService.SettingsOutcome.Settings.class,
                service.settings(ADMIN));
        assertEquals(12, settings.settings().get(0).value());
    }

    @Test
    void a_request_that_tells_a_rule_nothing_does_not_cost_the_elevation() {
        anAccountOf(ADMIN);
        theLengthAndTheDigitRuleAreLive();
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_SETTINGS);

        assertInstanceOf(
                AdminService.SetSettingOutcome.NoValue.class,
                service.setSetting(ADMIN, ACCESS_TOKEN, MinLength.KEY, null));
        assertInstanceOf(
                AdminService.SetPasswordMinLengthOutcome.NotANumber.class,
                service.setPasswordMinLength(ADMIN, ACCESS_TOKEN, null));
        assertInstanceOf(
                AdminService.SetPasswordMinLengthOutcome.NotANumber.class,
                service.setPasswordMinLength(ADMIN, ACCESS_TOKEN, "twelve"));

        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.ADMIN_SETTINGS));
    }

    @Test
    void a_value_the_rule_refuses_and_a_key_nobody_declared_write_nothing() {
        anAccountOf(ADMIN);
        theLengthAndTheDigitRuleAreLive();

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_SETTINGS);
        AdminService.SetSettingOutcome.Refused refused = assertInstanceOf(
                AdminService.SetSettingOutcome.Refused.class,
                service.setSetting(ADMIN, ACCESS_TOKEN, MinLength.KEY, Integer.toString(MinLength.BOUNDARY - 1)));
        assertEquals("minLength must be at least " + MinLength.BOUNDARY, refused.reason());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_SETTINGS);
        assertInstanceOf(
                AdminService.SetSettingOutcome.UnknownKey.class,
                service.setSetting(ADMIN, ACCESS_TOKEN, "security.nobody.declared.this", "1"));

        assertTrue(rows.isEmpty());
    }

    @Test
    void the_minimum_length_has_a_lever_of_its_own_under_the_same_action() {
        anAccountOf(ADMIN);
        theLengthAndTheDigitRuleAreLive();

        AdminService.SetPasswordMinLengthOutcome.StepUpRequired refused = assertInstanceOf(
                AdminService.SetPasswordMinLengthOutcome.StepUpRequired.class,
                service.setPasswordMinLength(ADMIN, ACCESS_TOKEN, "12"));
        assertEquals("admin-settings", refused.action());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_SETTINGS);
        AdminService.SetPasswordMinLengthOutcome.Accepted accepted = assertInstanceOf(
                AdminService.SetPasswordMinLengthOutcome.Accepted.class,
                service.setPasswordMinLength(ADMIN, ACCESS_TOKEN, " 12 "));
        assertEquals(12, accepted.value());
        assertEquals("12", rows.get(MinLength.KEY));

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_SETTINGS);
        assertInstanceOf(
                AdminService.SetPasswordMinLengthOutcome.Refused.class,
                service.setPasswordMinLength(ADMIN, ACCESS_TOKEN, Integer.toString(MinLength.BOUNDARY - 1)));
        assertEquals("12", rows.get(MinLength.KEY));
    }
}
