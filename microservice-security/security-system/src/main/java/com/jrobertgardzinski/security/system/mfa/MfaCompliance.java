package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.config.mfa.MfaPolicy;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether an account meets its role's factor floor. The count is the whole chain: a password
 * counts as the first factor (a federated, passwordless account's provider login does not), plus
 * the enrolled factors. A bootstrap admin is graced while they have zero factors — otherwise the
 * very first admin could never sign in to configure anything — and held to the floor the moment
 * they enrol their first.
 */
public class MfaCompliance {

    private final EnrolledFactorRepository enrolledFactorRepository;
    private final UserRepository userRepository;
    private final MfaPolicy policy;
    private final Set<String> bootstrapAdmins;

    public MfaCompliance(EnrolledFactorRepository enrolledFactorRepository, UserRepository userRepository,
                         MfaPolicy policy, Set<String> bootstrapAdmins) {
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.userRepository = userRepository;
        this.policy = policy;
        this.bootstrapAdmins = bootstrapAdmins.stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public int requiredFactors(Set<Role> roles) {
        return policy.requiredFactorCount(roles.stream().map(Role::name).collect(Collectors.toUnmodifiableSet()));
    }

    /** Factors that count toward the floor: the enrolled ones, plus the password if the account has one. */
    public int effectiveFactorCount(Email email) {
        int enrolled = enrolledFactorRepository.findByUser(email).size();
        return userRepository.isPasswordless(email) ? enrolled : enrolled + 1;
    }

    public boolean isCompliant(Email email, Set<Role> roles) {
        if (bootstrapAdmins.contains(email.value().toLowerCase(Locale.ROOT)) && enrolledFactorRepository.findByUser(email).isEmpty()) {
            return true;   // grace: the first admin bootstraps before enrolling anything
        }
        return effectiveFactorCount(email) >= requiredFactors(roles);
    }

    /** Whether removing one factor would leave the account below its floor (removal must be refused). */
    public boolean removalWouldBreakFloor(Email email, Set<Role> roles) {
        return effectiveFactorCount(email) - 1 < requiredFactors(roles);
    }
}
