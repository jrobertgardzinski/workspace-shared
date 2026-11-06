package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.RoleStatus;

import java.util.HashSet;
import java.util.Set;

public final class RequireRole {

    private final BootstrapAdmins bootstrapAdmins;
    private final RolesOf rolesOf;

    public RequireRole(BootstrapAdmins bootstrapAdmins, RolesOf rolesOf) {
        this.bootstrapAdmins = bootstrapAdmins;
        this.rolesOf = rolesOf;
    }

    /** {@code PASSED} when the caller holds the role; otherwise the one refusal that names it. */
    public RoleStatus check(Email caller, Role required) {
        if (rolesInForce(caller).contains(required)) {
            return RoleStatus.PASSED;
        }
        return switch (required) {
            case USER -> RoleStatus.NOT_A_USER;
            case MODERATOR -> RoleStatus.NOT_A_MODERATOR;
            case ADMIN -> RoleStatus.NOT_AN_ADMIN;
        };
    }

    public Set<Role> rolesInForce(Email caller) {
        Set<Role> roles = new HashSet<>(rolesOf.of(caller));
        if (bootstrapAdmins.contains(caller)) {
            roles.add(Role.ADMIN);
        }
        return Set.copyOf(roles);
    }
}
