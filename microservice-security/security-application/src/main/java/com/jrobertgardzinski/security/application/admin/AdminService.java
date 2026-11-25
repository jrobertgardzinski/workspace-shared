package com.jrobertgardzinski.security.application.admin;

import com.jrobertgardzinski.config.ConfigValue;
import com.jrobertgardzinski.config.Configuration;
import com.jrobertgardzinski.config.LiveKey;
import com.jrobertgardzinski.config.ladder.Resolution;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.config.MinLength;
import com.jrobertgardzinski.password.config.RequiresDigit;
import com.jrobertgardzinski.password.config.RequiresLowercase;
import com.jrobertgardzinski.password.config.RequiresUppercase;
import com.jrobertgardzinski.password.config.SpecialChars;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.mfa.SessionElevation;
import com.jrobertgardzinski.security.system.roles.RequireRole;
import com.jrobertgardzinski.security.system.roles.SetUserRoles;
import com.jrobertgardzinski.security.system.settings.SetSetting;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The bridge for an administrator's levers: other people's roles and factors, and the rules the
 * running system may be told. Strings in, plain values out.
 *
 * <p>Every lever has the same two gates, in the same order. The caller must be an ADMIN, from a
 * persisted grant or the deployment's bootstrap list: a non-admin learns only that they are not
 * one. And the caller must have just stepped up for this very action, because a lever pulled on a
 * stolen admin session outlives the session. What the request names is read between the two, so a
 * typo does not cost the one-shot elevation. Reading costs the first gate only.
 */
public final class AdminService {

    /** The password policy's rules, in the order its report lists them. */
    private static final List<String> PASSWORD_POLICY_KEYS = List.of(
            MinLength.KEY,
            SpecialChars.KEY,
            RequiresUppercase.KEY,
            RequiresLowercase.KEY,
            RequiresDigit.KEY);

    private final RequireRole requireRole;
    private final SetUserRoles setUserRoles;
    private final SetSetting setSetting;
    private final Configuration configuration;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final SessionElevation sessionElevation;
    private final TransactionBoundary transactionBoundary;

    public AdminService(
                    RequireRole requireRole,
                    SetUserRoles setUserRoles,
                    SetSetting setSetting,
                    Configuration configuration,
                    EnrolledFactorRepository enrolledFactorRepository,
                    SessionElevation sessionElevation,
                    TransactionBoundary transactionBoundary) {
        this.requireRole = requireRole;
        this.setUserRoles = setUserRoles;
        this.setSetting = setSetting;
        this.configuration = configuration;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.sessionElevation = sessionElevation;
        this.transactionBoundary = transactionBoundary;
    }

    public sealed interface ResetFactorsOutcome {

        /** Every factor of the account is gone; {@code user} is the address as it was asked about. */
        record FactorsReset(String user) implements ResetFactorsOutcome {}

        record NotAnAdmin() implements ResetFactorsOutcome {}

        /** Not an address the domain can construct. */
        record InvalidEmail() implements ResetFactorsOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements ResetFactorsOutcome {}
    }

    public sealed interface SetRolesOutcome {

        /** The roles the account holds now; {@code user} is the address as it was asked about. */
        record Updated(String user, List<String> roles) implements SetRolesOutcome {}

        record NoSuchUser() implements SetRolesOutcome {}

        /**
         * The change would leave nobody holding ADMIN. The caller IS allowed to do this; the state
         * of the system refuses. {@code roles} are the ones the account still holds.
         */
        record WouldLeaveNoAdmin(List<String> roles) implements SetRolesOutcome {}

        record NotAnAdmin() implements SetRolesOutcome {}

        /** Not an address the domain can construct. */
        record InvalidEmail() implements SetRolesOutcome {}

        /** No list of roles, or a name in it that is not one; {@code known} are the roles that exist. */
        record UnknownRole(List<String> known) implements SetRolesOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements SetRolesOutcome {}
    }

    /** A value some level of the ladder offered for a rule, and why the rule refused it. */
    public record RefusedValue(String source, Object value, String reason) {}

    /**
     * One live rule as an administrator reads it: what is in force, the level that answered, and
     * what was refused on the way, in the gate's own words. This is how an admin learns that a row
     * written at the database console is not the value the system uses.
     */
    public record Setting(String key, Object value, String source, List<RefusedValue> rejected) {}

    public sealed interface SettingsOutcome {

        /** In the order the rules were declared. */
        record Settings(List<Setting> settings) implements SettingsOutcome {}

        record NotAnAdmin() implements SettingsOutcome {}
    }

    public sealed interface SetSettingOutcome {

        /** The rule's value as it is now held. */
        record Accepted(Object value) implements SetSettingOutcome {}

        /** The rule's own gate refused the value, in its words; nothing was written. */
        record Refused(String reason) implements SetSettingOutcome {}

        /** No live rule goes by that key; nothing was written. */
        record UnknownKey(String reason) implements SetSettingOutcome {}

        /** The request told the rule nothing. */
        record NoValue() implements SetSettingOutcome {}

        record NotAnAdmin() implements SetSettingOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements SetSettingOutcome {}
    }

    public sealed interface SetPasswordMinLengthOutcome {

        /** The minimum length as it is now held. */
        record Accepted(Object value) implements SetPasswordMinLengthOutcome {}

        /** The rule's own gate refused the length, in its words; nothing was written. */
        record Refused(String reason) implements SetPasswordMinLengthOutcome {}

        record NotANumber() implements SetPasswordMinLengthOutcome {}

        record NotAnAdmin() implements SetPasswordMinLengthOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements SetPasswordMinLengthOutcome {}
    }

    /**
     * The recovery path when someone is locked out of every factor (a lost phone): wipes them, so
     * the account drops below its role floor and has to enrol again. The recovery codes stay.
     * {@code accessToken} is the bearer token as sent, or null when there was none.
     */
    public ResetFactorsOutcome resetFactors(String callerEmail, String accessToken, String userEmail) {

        Caller caller = callerOf(callerEmail);

        if (
                !isAdmin(caller)
        ) {
            return new ResetFactorsOutcome.NotAnAdmin();
        }

        Email user;
        try {
            user = Email.of(userEmail);
        } catch (IllegalArgumentException invalid) {
            return new ResetFactorsOutcome.InvalidEmail();
        }

        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.ADMIN_RESET)
        ) {
            return new ResetFactorsOutcome.StepUpRequired(
                    StepUpAction.ADMIN_RESET.wire());
        }

        transactionBoundary.execute(() -> {
            enrolledFactorRepository.removeAll(user);
            return null;
        });

        return new ResetFactorsOutcome.FactorsReset(userEmail);
    }

    /**
     * Grants or revokes another account's roles. {@code roles} are the names as the request
     * carried them, or null when it carried no list. {@code accessToken} is the bearer token as
     * sent, or null when there was none.
     */
    public SetRolesOutcome setRoles(String callerEmail, String accessToken, String userEmail, List<String> roles) {

        Caller caller = callerOf(callerEmail);

        if (
                !isAdmin(caller)
        ) {
            return new SetRolesOutcome.NotAnAdmin();
        }

        Email user;
        try {
            user = Email.of(userEmail);
        } catch (IllegalArgumentException invalid) {
            return new SetRolesOutcome.InvalidEmail();
        }

        Set<Role> wanted;
        try {
            wanted = rolesOf(roles);
        } catch (IllegalArgumentException unknownRole) {
            // the roles that exist, not the exception's sentence, which names the enum's class
            return new SetRolesOutcome.UnknownRole(
                    namesOf(Arrays.asList(Role.values())));
        }

        // a granted role is a permanent widening of what a session may do, so a stolen admin
        // session must prove itself again before handing one out
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.ADMIN_ROLES)
        ) {
            return new SetRolesOutcome.StepUpRequired(
                    StepUpAction.ADMIN_ROLES.wire());
        }

        SetUserRoles.Result result = setUserRoles.execute(user, wanted);

        return switch (result) {
            case SetUserRoles.Result.Updated updated ->
                    new SetRolesOutcome.Updated(
                            userEmail,
                            namesOf(updated.roles()));
            case SetUserRoles.Result.NoSuchUser noSuchUser ->
                    new SetRolesOutcome.NoSuchUser();
            case SetUserRoles.Result.WouldLeaveNoAdmin wouldLeaveNoAdmin ->
                    new SetRolesOutcome.WouldLeaveNoAdmin(
                            namesOf(wouldLeaveNoAdmin.roles()));
        };
    }

    /** Every rule declared live, with what is in force under it and where that came from. */
    public SettingsOutcome settings(String callerEmail) {

        Caller caller = callerOf(callerEmail);

        if (
                !isAdmin(caller)
        ) {
            return new SettingsOutcome.NotAnAdmin();
        }

        return new SettingsOutcome.Settings(
                configuration.liveKeys().values().stream()
                        .map(AdminService::settingOf)
                        .toList());
    }

    /** The password policy in force: every rule of it under its key, with its provenance. */
    public SettingsOutcome passwordPolicy(String callerEmail) {

        Caller caller = callerOf(callerEmail);

        if (
                !isAdmin(caller)
        ) {
            return new SettingsOutcome.NotAnAdmin();
        }

        return new SettingsOutcome.Settings(
                PASSWORD_POLICY_KEYS.stream()
                        .map(configuration::liveKey)
                        .flatMap(Optional::stream)
                        .map(AdminService::settingOf)
                        .toList());
    }

    /**
     * Tells one live rule a value, by its key. {@code value} is the text of what the request
     * carried, or null when it carried none. {@code accessToken} is the bearer token as sent, or
     * null when there was none.
     */
    public SetSettingOutcome setSetting(String callerEmail, String accessToken, String key, String value) {

        Caller caller = callerOf(callerEmail);

        if (
                !isAdmin(caller)
        ) {
            return new SetSettingOutcome.NotAnAdmin();
        }

        if (
                value == null
        ) {
            return new SetSettingOutcome.NoValue();
        }

        // a rule binds every future decision under it, so every write takes a fresh step-up
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.ADMIN_SETTINGS)
        ) {
            return new SetSettingOutcome.StepUpRequired(
                    StepUpAction.ADMIN_SETTINGS.wire());
        }

        SetSetting.Result result = setSetting.execute(key, value);

        return switch (result) {
            case SetSetting.Result.Accepted accepted ->
                    new SetSettingOutcome.Accepted(
                            accepted.value());
            case SetSetting.Result.Refused refused ->
                    new SetSettingOutcome.Refused(
                            refused.reason());
            case SetSetting.Result.UnknownKey unknownKey ->
                    new SetSettingOutcome.UnknownKey(
                            unknownKey.reason());
        };
    }

    /**
     * The password policy's minimum length, set by name: the one rule with a lever of its own.
     * {@code value} is the text of what the request carried, or null when it carried none.
     * {@code accessToken} is the bearer token as sent, or null when there was none.
     */
    public SetPasswordMinLengthOutcome setPasswordMinLength(String callerEmail, String accessToken, String value) {

        Caller caller = callerOf(callerEmail);

        if (
                !isAdmin(caller)
        ) {
            return new SetPasswordMinLengthOutcome.NotAnAdmin();
        }

        int requested;
        try {
            requested = Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException notANumber) {
            return new SetPasswordMinLengthOutcome.NotANumber();
        }

        // the floor binds every future password in the estate
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.ADMIN_SETTINGS)
        ) {
            return new SetPasswordMinLengthOutcome.StepUpRequired(
                    StepUpAction.ADMIN_SETTINGS.wire());
        }

        SetSetting.Result result = setSetting.execute(
                MinLength.KEY,
                Integer.toString(requested));

        return switch (result) {
            case SetSetting.Result.Accepted accepted ->
                    new SetPasswordMinLengthOutcome.Accepted(
                            accepted.value());
            case SetSetting.Result.Refused refused ->
                    new SetPasswordMinLengthOutcome.Refused(
                            refused.reason());
            // this lever names its key itself, so an unknown one is still the caller's refusal
            case SetSetting.Result.UnknownKey unknownKey ->
                    new SetPasswordMinLengthOutcome.Refused(
                            unknownKey.reason());
        };
    }

    /** Asked about ADMIN only, so whichever refusal comes back, the caller is not one. */
    private boolean isAdmin(Caller caller) {
        try {
            requireRole.check(caller, Role.ADMIN);
            return true;
        } catch (RequireRole.NotAUser | RequireRole.NotAModerator | RequireRole.NotAnAdmin refused) {
            return false;
        }
    }

    private static Set<Role> rolesOf(List<String> names) {
        if (
                names == null
        ) {
            throw new IllegalArgumentException("roles must be a list");
        }

        return names.stream()
                .map(name -> Role.valueOf(name.trim().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> namesOf(Collection<Role> roles) {
        return roles.stream()
                .map(Role::name)
                .sorted()
                .toList();
    }

    private static Setting settingOf(LiveKey liveKey) {
        Resolution<? extends ConfigValue<?>> resolution = liveKey.resolution();

        return new Setting(
                liveKey.key(),
                resolution.value().value(),
                resolution.source(),
                resolution.rejected().stream()
                        .map(rejected -> new RefusedValue(rejected.source(), rejected.value(), rejected.reason()))
                        .toList());
    }

    private static Caller callerOf(String callerEmail) {
        return new Caller(
                Email.of(callerEmail));
    }
}
