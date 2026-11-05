package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.Optional;
import java.util.Set;

/**
 * Grant or revoke a user's roles — the admin action behind flat RBAC. The target must exist; USER
 * can never be removed (it is what "signed in" means). WHO may call this is the boundary's concern
 * (only an admin); this use case is the state change and its outcome.
 *
 * <p>One change is refused: the one that leaves nobody holding ADMIN. Granting roles is itself an
 * admin action, so the administrator who drops their own grant while being the last one locks the
 * door from the inside — nobody left can grant it back. The only way out is the deployment's
 * {@code security.bootstrap-admins}, which is why a deployment that names somebody there is allowed
 * to go ahead: there, an admin still exists, by configuration rather than by a row.
 */
public class SetUserRoles {

    private final UserRepository userRepository;
    private final BootstrapAdmins bootstrapAdmins;

    public SetUserRoles(
                    UserRepository userRepository,
                    BootstrapAdmins bootstrapAdmins) {
        this.userRepository = userRepository;
        this.bootstrapAdmins = bootstrapAdmins;
    }

    public sealed interface Result {

        /** The roles the account holds now. */
        record Updated(Set<Role> roles) implements Result {}

        record NoSuchUser() implements Result {}

        /** The roles the account still holds, so a client can show the truth. */
        record WouldLeaveNoAdmin(Set<Role> roles) implements Result {}
    }

    public Result execute(Email target, Set<Role> roles) {

        Optional<User> user = userRepository.findBy(target);

        if (
                user.isEmpty()
        ) {
            return new Result.NoSuchUser();
        }

        if (
                wouldLeaveNoAdmin(user.get(), roles)
        ) {
            return new Result.WouldLeaveNoAdmin(
                    user.get().roles());
        }

        userRepository.setRoles(target, roles);

        return new Result.Updated(
                userRepository.findBy(target).map(User::roles).orElse(Set.of(Role.USER)));
    }

    /**
     * Is this the change that removes the last ADMIN there is?
     *
     * <p>Only a removal can do it, and only when this account is the sole admin ROW and the
     * deployment declares no bootstrap admin. Counting rows is the repository's job; combining that
     * with what the deployment says is this use case's.
     */
    private boolean wouldLeaveNoAdmin(User user, Set<Role> roles) {
        boolean losesAdmin = user.hasRole(Role.ADMIN) && !roles.contains(Role.ADMIN);
        return losesAdmin && userRepository.countAdmins() <= 1 && bootstrapAdmins.admins().isEmpty();
    }
}
