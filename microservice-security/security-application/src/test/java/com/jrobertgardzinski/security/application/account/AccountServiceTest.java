package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.clock.AdjustableClock;
import com.jrobertgardzinski.email.config.EmailPolicy;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.AdmitEmail;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.application.PassThroughTransactionBoundary;
import com.jrobertgardzinski.security.application.feature.support.FakeHashAlgorithm;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeMailedLinkRepository;
import com.jrobertgardzinski.security.domain.repository.FakeSessionRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.account.ChangePassword;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChange;
import com.jrobertgardzinski.security.system.account.RequestEmailChange;
import com.jrobertgardzinski.security.system.mfa.SessionElevation;
import com.jrobertgardzinski.security.system.password.VerifyPassword;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge takes strings and answers in plain values; what it decides on its own is the order of
 * the gates in front of a change, and who is told by mail when an address is taken.
 */
class AccountServiceTest {

    private static final String SOURCE = "192.168.0.1";
    private static final String OWNER = "owner@example.com";
    private static final String PASSWORD = "StrongPassword1!";
    private static final String ACCESS_TOKEN = "the-access-token";

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private final FakeUserRepository users = new FakeUserRepository(clock);
    private final FakeMailedLinkRepository links = new FakeMailedLinkRepository(clock);
    private final FakeHashAlgorithm hashAlgorithm = new FakeHashAlgorithm();
    private final List<String> changeLinks = new ArrayList<>();
    private final List<String> alreadyRegisteredNotices = new ArrayList<>();
    private final Elevations elevations = new Elevations();

    private final AccountService service = service(10);

    /** One-shot elevations, as the real store keeps them. */
    private static final class Elevations implements SessionElevation {

        private final Set<String> granted = new HashSet<>();

        @Override
        public void elevate(String accessToken, StepUpAction action) {
            granted.add(accessToken + "|" + action);
        }

        @Override
        public boolean consume(String accessToken, StepUpAction action) {
            return granted.remove(accessToken + "|" + action);
        }
    }

    private AccountService service(int maxPerWindow) {
        FakeSessionRepository sessions = new FakeSessionRepository(clock);
        CreatePasswordHash createPasswordHash = new CreatePasswordHash(hashAlgorithm, PasswordPolicy::withDefaults);
        return new AccountService(
                new ChangePassword(users, new VerifyPassword(users, hashAlgorithm), createPasswordHash, sessions),
                new RequestEmailChange(
                        users,
                        links,
                        (email, token) -> changeLinks.add(email.value()),
                        new AdmitEmail(new EmailPolicy())),
                new ConfirmEmailChange(
                        links, users, new FakeEnrolledFactorRepository(), sessions,
                        Duration.ofMinutes(1440), clock),
                email -> alreadyRegisteredNotices.add(email.value()),
                elevations,
                new EmailPolicy(),
                new SourceThrottle(maxPerWindow, Duration.ofMinutes(15), clock),
                new PassThroughTransactionBoundary());
    }

    private void theOwnerExists() {
        users.save(new User(Email.of(OWNER), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
    }

    @Test
    void the_right_current_password_changes_it() {
        theOwnerExists();

        AccountService.ChangePasswordOutcome outcome =
                service.changePassword(OWNER, PASSWORD, "AnotherStrongPassword2!", SOURCE);

        assertInstanceOf(AccountService.ChangePasswordOutcome.Changed.class, outcome);
    }

    @Test
    void a_wrong_current_password_is_not_an_account_that_is_gone() {
        theOwnerExists();

        assertInstanceOf(
                AccountService.ChangePasswordOutcome.WrongPassword.class,
                service.changePassword(OWNER, "WrongButStrongPassword1!", "AnotherStrongPassword2!", SOURCE));
        assertInstanceOf(
                AccountService.ChangePasswordOutcome.AccountGone.class,
                service.changePassword("gone@example.com", PASSWORD, "AnotherStrongPassword2!", SOURCE));
    }

    @Test
    void a_missing_or_blank_password_is_said_before_anything_is_checked() {
        theOwnerExists();

        assertInstanceOf(
                AccountService.ChangePasswordOutcome.MissingPassword.class,
                service.changePassword(OWNER, null, "AnotherStrongPassword2!", SOURCE));
        assertInstanceOf(
                AccountService.ChangePasswordOutcome.MissingPassword.class,
                service.changePassword(OWNER, PASSWORD, "   ", SOURCE));
    }

    @Test
    void a_weak_new_password_comes_back_as_broken_rules() {
        theOwnerExists();

        AccountService.ChangePasswordOutcome.Rejected rejected = assertInstanceOf(
                AccountService.ChangePasswordOutcome.Rejected.class,
                service.changePassword(OWNER, PASSWORD, "weak", SOURCE));

        assertFalse(rejected.passwordErrors().isEmpty());
    }

    @Test
    void a_source_that_spent_its_window_is_throttled_even_with_a_missing_password() {
        theOwnerExists();
        AccountService throttled = service(1);
        throttled.changePassword(OWNER, PASSWORD, "AnotherStrongPassword2!", SOURCE);

        // the window is counted first: a request with nothing in it still spends an attempt
        assertInstanceOf(
                AccountService.ChangePasswordOutcome.Throttled.class,
                throttled.changePassword(OWNER, null, null, SOURCE));
    }

    @Test
    void a_change_of_address_needs_a_fresh_elevation_and_spends_it() {
        theOwnerExists();

        AccountService.RequestEmailChangeOutcome.StepUpRequired refused = assertInstanceOf(
                AccountService.RequestEmailChangeOutcome.StepUpRequired.class,
                service.requestEmailChange(OWNER, ACCESS_TOKEN, "new@example.com"));
        assertEquals("change-email", refused.action());

        elevations.elevate(ACCESS_TOKEN, StepUpAction.CHANGE_EMAIL);
        assertInstanceOf(
                AccountService.RequestEmailChangeOutcome.Requested.class,
                service.requestEmailChange(OWNER, ACCESS_TOKEN, "new@example.com"));
        assertEquals(List.of("new@example.com"), changeLinks);

        // one-shot: the same token does not open the door twice
        assertInstanceOf(
                AccountService.RequestEmailChangeOutcome.StepUpRequired.class,
                service.requestEmailChange(OWNER, ACCESS_TOKEN, "other@example.com"));
    }

    @Test
    void a_request_without_a_bearer_token_has_not_stepped_up() {
        theOwnerExists();

        assertInstanceOf(
                AccountService.RequestEmailChangeOutcome.StepUpRequired.class,
                service.requestEmailChange(OWNER, null, "new@example.com"));
    }

    @Test
    void a_typo_in_the_new_address_does_not_cost_the_elevation() {
        theOwnerExists();
        elevations.elevate(ACCESS_TOKEN, StepUpAction.CHANGE_EMAIL);

        assertInstanceOf(
                AccountService.RequestEmailChangeOutcome.InvalidEmail.class,
                service.requestEmailChange(OWNER, ACCESS_TOKEN, "not-an-address"));

        // the elevation is still there for the corrected attempt
        assertInstanceOf(
                AccountService.RequestEmailChangeOutcome.Requested.class,
                service.requestEmailChange(OWNER, ACCESS_TOKEN, "new@example.com"));
    }

    @Test
    void a_taken_address_is_named_in_the_outcome_and_its_owner_is_told_by_mail() {
        theOwnerExists();
        users.save(new User(Email.of("taken@example.com"), hashAlgorithm.hash(PlaintextPassword.of(PASSWORD))));
        elevations.elevate(ACCESS_TOKEN, StepUpAction.CHANGE_EMAIL);

        assertInstanceOf(
                AccountService.RequestEmailChangeOutcome.EmailTaken.class,
                service.requestEmailChange(OWNER, ACCESS_TOKEN, "taken@example.com"));

        assertEquals(List.of("taken@example.com"), alreadyRegisteredNotices);
        assertTrue(changeLinks.isEmpty());
    }

    @Test
    void a_token_that_cannot_be_constructed_is_unreadable_not_an_exception() {
        assertInstanceOf(
                AccountService.ConfirmEmailChangeOutcome.UnreadableToken.class,
                service.confirmEmailChange(null));
        assertInstanceOf(
                AccountService.ConfirmEmailChangeOutcome.TokenNotFound.class,
                service.confirmEmailChange("nobody-was-sent-this"));
    }
}
