package com.jrobertgardzinski.security.application.identity;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.DisplayName;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.system.identity.DisplayNames;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;
import com.jrobertgardzinski.security.system.roles.RequireRole;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bridge for the two questions the rest of the estate asks about people: who the caller is,
 * and what to call the accounts behind a list of ids. Strings in, plain values out.
 */
public final class IdentityService {

    /** How many ids one question may carry. */
    public static final int MAX_IDS = 100;

    private final RequireRole requireRole;
    private final MfaCompliance mfaCompliance;
    private final UserRepository userRepository;
    private final DisplayNames displayNames;
    private final SourceThrottle displayNamesThrottle;

    public IdentityService(
                    RequireRole requireRole,
                    MfaCompliance mfaCompliance,
                    UserRepository userRepository,
                    DisplayNames displayNames,
                    SourceThrottle displayNamesThrottle) {
        this.requireRole = requireRole;
        this.mfaCompliance = mfaCompliance;
        this.userRepository = userRepository;
        this.displayNames = displayNames;
        this.displayNamesThrottle = displayNamesThrottle;
    }

    /**
     * The caller as other services and the UI see them. The MFA figures are the role floor, so a
     * consumer can nudge an under-protected privileged account.
     */
    public record Me(
            String id,
            String email,
            List<String> roles,
            boolean mfaCompliant,
            int requiredFactors,
            int haveFactors) {}

    /** One account's public name, under the id that was asked about. */
    public record NamedUser(String id, String displayName) {}

    public sealed interface DisplayNamesOutcome {

        /** In the order asked; a deleted or unknown id is simply not in the answer. */
        record Names(List<NamedUser> users) implements DisplayNamesOutcome {}

        record InvalidId() implements DisplayNamesOutcome {}

        record TooManyIds(int max) implements DisplayNamesOutcome {}

        record Throttled(long retryAfterSeconds) implements DisplayNamesOutcome {}
    }

    public Me me(String callerEmail) {

        Email email = Email.of(callerEmail);

        // rolesInForce, not the persisted grants: a BOOTSTRAP ADMIN holds ADMIN by configuration and
        // has no row saying so. Reading the row alone told such an administrator they were a plain
        // USER — here and in the JWT — while /admin/** let them in, so every consumer that gates on
        // this answer disagreed with the service that issued it.
        Set<Role> roles = requireRole.rolesInForce(email);

        return new Me(
                userRepository.findBy(email).map(user -> user.id().toString()).orElseThrow(),
                email.value(),
                roles.stream().map(Role::name).sorted().toList(),
                mfaCompliance.isCompliant(email, roles),
                mfaCompliance.requiredFactors(roles),
                mfaCompliance.effectiveFactorCount(email));
    }

    /** {@code commaSeparatedIds} as the query carried it: blanks and repeats are dropped. */
    public DisplayNamesOutcome displayNames(String commaSeparatedIds, String ipAddress) {

        // a per-source window keeps a crawler from sweeping the id space at line speed
        SourceThrottle.Decision decision = displayNamesThrottle.check(
                new IpAddress(ipAddress));

        if (
                !decision.allowed()
        ) {
            return new DisplayNamesOutcome.Throttled(
                    decision.retryAfterSeconds());
        }

        List<UserId> asked;
        try {
            asked = Arrays.stream(commaSeparatedIds.split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .map(UserId::of)
                    .distinct()
                    .toList();
        } catch (IllegalArgumentException notAnId) {
            return new DisplayNamesOutcome.InvalidId();
        }

        if (
                asked.size() > MAX_IDS
        ) {
            return new DisplayNamesOutcome.TooManyIds(MAX_IDS);
        }

        Map<UserId, DisplayName> names = displayNames.of(asked);

        return new DisplayNamesOutcome.Names(
                asked.stream()
                        .filter(names::containsKey)
                        .map(id -> new NamedUser(id.toString(), names.get(id).value()))
                        .toList());
    }
}
