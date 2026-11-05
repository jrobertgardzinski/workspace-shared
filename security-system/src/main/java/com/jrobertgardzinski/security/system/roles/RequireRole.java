package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.RoleError;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public final class RequireRole {

    private final BootstrapAdmins bootstrapAdmins;
    private final RolesOf rolesOf;

    public RequireRole(BootstrapAdmins bootstrapAdmins, RolesOf rolesOf) {
        this.bootstrapAdmins = bootstrapAdmins;
        this.rolesOf = rolesOf;
    }

    /** Empty when the caller holds the role; otherwise the one refusal that names it. */
    public List<RoleError> check(Email caller, Role required) {
        List<RoleError> errors = new LinkedList<>();
        if (!rolesInForce(caller).contains(required)) {
            errors.add(switch (required) {
                case USER -> RoleError.NOT_A_USER;
                case MODERATOR -> RoleError.NOT_A_MODERATOR;
                case ADMIN -> RoleError.NOT_AN_ADMIN;
            });
        }
        return errors;
    }

    public Set<Role> rolesInForce(Email caller) {
        Set<Role> roles = new HashSet<>(rolesOf.of(caller));
        if (bootstrapAdmins.contains(caller)) {
            roles.add(Role.ADMIN);
        }
        return Set.copyOf(roles);
    }
}
