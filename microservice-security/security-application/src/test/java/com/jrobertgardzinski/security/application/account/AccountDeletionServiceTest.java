package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.OneShotElevations;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.FakeSessionRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.AccountClosure;
import com.jrobertgardzinski.security.domain.vo.DeletionInitiator;
import com.jrobertgardzinski.security.domain.vo.PurgeChoices;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.account.StartAccountDeletion;
import com.jrobertgardzinski.security.system.roles.BootstrapAdmins;
import com.jrobertgardzinski.security.system.roles.RequireRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is which of the
 * two roads a request is on, your own account or somebody else's, and what each road asks for
 * before the account is locked.
 */
class AccountDeletionServiceTest {

    private static final String OWNER = "owner@example.com";
    private static final String ADMIN = "admin@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String ACCESS_TOKEN = "the-access-token";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();
    private final OneShotElevations elevations = new OneShotElevations();
    /** What the content services were asked to purge. */
    private final List<AccountClosure> begun = new ArrayList<>();

    private final AccountDeletionService service = new AccountDeletionService(
            new StartAccountDeletion(users, new FakeSessionRepository(clock), begun::add),
            users,
            new RequireRole(
                    BootstrapAdmins.of(List.of(ADMIN)),
                    email -> users.findBy(email).map(User::roles).orElse(Set.of())),
            elevations,
            new PassThroughTransactionBoundary());

    private void anAccountOf(String email) {
        users.save(new User(Email.of(email), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
    }

    @Test
    void closing_your_own_account_needs_a_fresh_elevation_and_then_locks_it() {
        anAccountOf(OWNER);

        AccountDeletionService.StartOutcome.StepUpRequired refused = assertInstanceOf(
                AccountDeletionService.StartOutcome.StepUpRequired.class,
                service.start(OWNER, ACCESS_TOKEN, OWNER, null));
        assertEquals("delete-account", refused.action());
        assertFalse(users.isPendingDeletion(Email.of(OWNER)));

        elevations.elevate(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT);
        assertInstanceOf(
                AccountDeletionService.StartOutcome.OwnAccountClosing.class,
                service.start(OWNER, ACCESS_TOKEN, OWNER, null));

        assertTrue(users.isPendingDeletion(Email.of(OWNER)));
        assertEquals(DeletionInitiator.SELF, begun.get(0).requestedBy());
    }

    @Test
    void a_request_to_be_forgotten_states_no_conditions_even_when_it_sends_some() {
        anAccountOf(OWNER);
        elevations.elevate(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT);

        // not even an over-wide map is looked at: nothing in it could change the outcome
        assertInstanceOf(
                AccountDeletionService.StartOutcome.OwnAccountClosing.class,
                service.start(OWNER, ACCESS_TOKEN, OWNER, anOverWidePurgeMap()));

        assertEquals(PurgeChoices.serviceDefaults(), begun.get(0).choices());
    }

    @Test
    void the_same_account_spelled_differently_is_still_your_own_and_travels_under_the_tokens_spelling() {
        anAccountOf(OWNER);
        elevations.elevate(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT);

        assertInstanceOf(
                AccountDeletionService.StartOutcome.OwnAccountClosing.class,
                service.start(OWNER, ACCESS_TOKEN, "OWNER@EXAMPLE.COM", null));

        assertEquals(OWNER, begun.get(0).target().value());
    }

    @Test
    void closing_somebody_elses_account_is_an_administrators_and_a_non_admin_learns_only_that() {
        anAccountOf(OWNER);
        anAccountOf("stranger@example.com");
        elevations.elevate(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT);
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_DELETE_ACCOUNT);

        assertInstanceOf(
                AccountDeletionService.StartOutcome.NotAnAdmin.class,
                service.start(OWNER, ACCESS_TOKEN, "stranger@example.com", null));
        // not even whether the address they named exists
        assertInstanceOf(
                AccountDeletionService.StartOutcome.NotAnAdmin.class,
                service.start(OWNER, ACCESS_TOKEN, "nobody@example.com", null));

        assertTrue(begun.isEmpty());
        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.ADMIN_DELETE_ACCOUNT));
    }

    @Test
    void an_administrator_steps_up_for_that_very_action_and_may_say_what_happens_to_the_content() {
        anAccountOf(ADMIN);
        anAccountOf(OWNER);
        // the elevation for closing one's OWN account does not open somebody else's
        elevations.elevate(ACCESS_TOKEN, StepUpAction.DELETE_ACCOUNT);

        AccountDeletionService.StartOutcome.StepUpRequired refused = assertInstanceOf(
                AccountDeletionService.StartOutcome.StepUpRequired.class,
                service.start(ADMIN, ACCESS_TOKEN, OWNER, null));
        assertEquals("admin-delete-account", refused.action());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_DELETE_ACCOUNT);
        AccountDeletionService.StartOutcome.SomebodyElsesClosing closing = assertInstanceOf(
                AccountDeletionService.StartOutcome.SomebodyElsesClosing.class,
                service.start(ADMIN, ACCESS_TOKEN, OWNER, Map.of("memes", "KEEP_POPULAR_ANONYMIZED:100")));

        assertEquals(OWNER, closing.user());
        assertEquals(ADMIN, closing.administrator());
        assertTrue(users.isPendingDeletion(Email.of(OWNER)));
        assertEquals(DeletionInitiator.ADMIN, begun.get(0).requestedBy());
        assertEquals(Map.of("memes", "KEEP_POPULAR_ANONYMIZED:100"), begun.get(0).choices().rules());
    }

    @Test
    void a_purge_map_too_wide_to_travel_is_refused_before_the_elevation_is_spent() {
        anAccountOf(ADMIN);
        anAccountOf(OWNER);
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_DELETE_ACCOUNT);

        assertInstanceOf(
                AccountDeletionService.StartOutcome.InvalidPurgeChoices.class,
                service.start(ADMIN, ACCESS_TOKEN, OWNER, anOverWidePurgeMap()));

        assertTrue(begun.isEmpty());
        assertTrue(elevations.holds(ACCESS_TOKEN, StepUpAction.ADMIN_DELETE_ACCOUNT));
    }

    @Test
    void an_address_nobody_holds_starts_no_saga() {
        anAccountOf(ADMIN);
        elevations.elevate(ACCESS_TOKEN, StepUpAction.ADMIN_DELETE_ACCOUNT);

        assertInstanceOf(
                AccountDeletionService.StartOutcome.NoSuchUser.class,
                service.start(ADMIN, ACCESS_TOKEN, "nobody@example.com", null));

        assertTrue(begun.isEmpty());
    }

    @Test
    void an_address_that_cannot_be_constructed_is_said_before_anything_else() {
        anAccountOf(OWNER);

        assertInstanceOf(
                AccountDeletionService.StartOutcome.InvalidEmail.class,
                service.start(OWNER, ACCESS_TOKEN, "not-an-address", null));
    }

    private static Map<String, String> anOverWidePurgeMap() {
        Map<String, String> purge = new HashMap<>();
        for (int axis = 0; axis <= PurgeChoices.MAX_AXES; axis++) {
            purge.put("axis-" + axis, "KEEP");
        }
        return purge;
    }
}
