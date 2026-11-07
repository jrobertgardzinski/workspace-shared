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

    /** {@link RoleStatus.Passed} when the caller holds the role; otherwise the one refusal that names it. */
    public RoleStatus check(Email caller, Role required) {
        if (rolesInForce(caller).contains(required)) {
            return new RoleStatus.Passed();
        }
        return switch (required) {
            case USER -> new RoleStatus.NotAUser();
            case MODERATOR -> new RoleStatus.NotAModerator();
            case ADMIN -> new RoleStatus.NotAnAdmin();
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
