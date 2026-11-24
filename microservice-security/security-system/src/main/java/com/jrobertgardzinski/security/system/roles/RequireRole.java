package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.HashSet;
import java.util.Set;

public final class RequireRole {

    private final BootstrapAdmins bootstrapAdmins;
    private final RolesOf rolesOf;

    public RequireRole(BootstrapAdmins bootstrapAdmins, RolesOf rolesOf) {
        this.bootstrapAdmins = bootstrapAdmins;
        this.rolesOf = rolesOf;
    }

    /**
     * The caller lacks the role: one refusal per role, so a non-admin learns only that they are not
     * one. The wire code for each ({@code NOT_AN_ADMIN}) is the edge's to spell.
     */
    public static final class NotAUser extends Exception {}

    public static final class NotAModerator extends Exception {}

    public static final class NotAnAdmin extends Exception {}

    /** Returns when the caller holds the role; otherwise throws the one refusal that names it. */
    public void check(Caller caller, Role required) throws NotAUser, NotAModerator, NotAnAdmin {
        if (rolesInForce(caller.email()).contains(required)) {
            return;
        }
        switch (required) {
            case USER -> throw new NotAUser();
            case MODERATOR -> throw new NotAModerator();
            case ADMIN -> throw new NotAnAdmin();
        }
    }

    public Set<Role> rolesInForce(Email caller) {
        Set<Role> roles = new HashSet<>(rolesOf.of(caller));
        if (bootstrapAdmins.contains(caller)) {
            roles.add(Role.ADMIN);
        }
        return Set.copyOf(roles);
    }
}
