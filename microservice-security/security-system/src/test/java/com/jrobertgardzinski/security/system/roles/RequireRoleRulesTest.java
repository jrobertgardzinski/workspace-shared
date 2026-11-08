package com.jrobertgardzinski.security.system.roles;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.RoleStatus;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Security")
@Feature("Roles")
@Story("Requiring a role: the store's grant or the deployment's bootstrap list, refused by code otherwise")
class RequireRoleRulesTest {

    private static final Email ADMIN = Email.of("admin@example.com");
    private static final Email MEMBER = Email.of("member@example.com");
    private static final Email STRANGER = Email.of("stranger@example.com");

    private static RolesOf store(Map<Email, Set<Role>> granted) {
        return email -> granted.getOrDefault(email, Set.of());
    }

    @Example
    @Label("a role the store granted satisfies the requirement")
    void grantedRoleSatisfies() {
        RequireRole require = new RequireRole(BootstrapAdmins.none(), store(Map.of(ADMIN, Set.of(Role.USER, Role.ADMIN))));
        assertThat(require.check(new Caller(ADMIN), Role.ADMIN)).isInstanceOf(RoleStatus.Passed.class);
        assertThat(require.rolesInForce(ADMIN)).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
    }

    @Example
    @Label("a bootstrap admin is ADMIN before any grant exists")
    void bootstrapAdminIsAdmin() {
        RequireRole require = new RequireRole(BootstrapAdmins.of(List.of(" Admin@Example.com ")), store(Map.of()));
        assertThat(require.check(new Caller(ADMIN), Role.ADMIN)).isInstanceOf(RoleStatus.Passed.class);
        assertThat(require.rolesInForce(ADMIN)).contains(Role.ADMIN);
    }

    @Example
    @Label("a USER without the role is refused with NOT_AN_ADMIN")
    void memberIsRefused() {
        RequireRole require = new RequireRole(BootstrapAdmins.none(), store(Map.of(MEMBER, Set.of(Role.USER))));
        assertThat(require.check(new Caller(MEMBER), Role.ADMIN)).isInstanceOf(RoleStatus.NotAnAdmin.class);
    }

    @Example
    @Label("an identity the store does not know has no roles at all")
    void strangerHasNoRoles() {
        RequireRole require = new RequireRole(BootstrapAdmins.none(), store(Map.of()));
        assertThat(require.rolesInForce(STRANGER)).isEmpty();
        assertThat(require.check(new Caller(STRANGER), Role.USER)).isInstanceOf(RoleStatus.NotAUser.class);
    }

    @Property
    @Label("every role has a refusal code of its own, and it names the missing role")
    void everyRoleHasItsOwnCode(@ForAll("roles") Role required) {
        Allure.parameter("required", required);
        RequireRole require = new RequireRole(BootstrapAdmins.none(), store(Map.of()));
        Class<? extends RoleStatus> refusal = switch (required) {
            case USER -> RoleStatus.NotAUser.class;
            case MODERATOR -> RoleStatus.NotAModerator.class;
            case ADMIN -> RoleStatus.NotAnAdmin.class;
        };
        assertThat(require.check(new Caller(STRANGER), required)).isInstanceOf(refusal);
    }

    @Provide
    Arbitrary<Role> roles() {
        return Arbitraries.of(Role.class);
    }

    @Example
    @Label("a blank bootstrap entry is ignored, an invalid one is refused at declaration")
    void bootstrapListIsParsedNotTrusted() {
        assertThat(BootstrapAdmins.of(List.of("", "  ")).admins()).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> BootstrapAdmins.of(List.of("not-an-address")))
                .isInstanceOf(RuntimeException.class);
    }
}
