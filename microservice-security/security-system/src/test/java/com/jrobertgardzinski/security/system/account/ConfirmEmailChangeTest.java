package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.repository.MailedLinkRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Change email")
class ConfirmEmailChangeTest {

    private static final VerificationToken TOKEN = new VerificationToken("change-token");
    private static final Email OLD = Email.of("user@example.com");
    private static final Email NEW = Email.of("new@example.com");

    private MailedLinkRepository mailedLinkRepository;
    private UserRepository userRepository;
    private EnrolledFactorRepository enrolledFactorRepository;
    private SessionRepository sessionRepository;
    private ConfirmEmailChange confirmEmailChange;

    private static final int TOKEN_TTL_MINUTES = 1440;
    private static final java.time.LocalDateTime NOW =
            java.time.LocalDateTime.of(2026, 7, 30, 12, 0);
    private final java.time.Clock clock = java.time.Clock.fixed(
            NOW.toInstant(java.time.ZoneOffset.UTC), java.time.ZoneOffset.UTC);

    @BeforeTry
    void init() {
        mailedLinkRepository = Mockito.mock(MailedLinkRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        enrolledFactorRepository = Mockito.mock(EnrolledFactorRepository.class);
        sessionRepository = Mockito.mock(SessionRepository.class);
        confirmEmailChange = new ConfirmEmailChange(mailedLinkRepository, userRepository,
                enrolledFactorRepository,
                sessionRepository, java.time.Duration.ofMinutes(TOKEN_TTL_MINUTES), clock);
    }

    @Example
    @Label("A matching token moves the user to the new address and marks it verified")
    void matching_token_changes_the_email() {
        Mockito.when(mailedLinkRepository.confirmChange(TOKEN)).thenReturn(Optional.of(fresh(new EmailChange(OLD, NEW))));

        assertEquals(new ConfirmEmailChange.Result.EmailChanged(OLD, NEW), confirmEmailChange.execute(TOKEN));
        Mockito.verify(userRepository).updateEmail(OLD, NEW);
        Mockito.verify(userRepository).markVerified(NEW);
    }

    @Example
    @Label("An address taken while the link sat in the mailbox is refused, and nothing is moved")
    void refuses_an_address_taken_in_the_meantime() {
        Mockito.when(mailedLinkRepository.confirmChange(TOKEN)).thenReturn(Optional.of(fresh(new EmailChange(OLD, NEW))));
        Mockito.when(userRepository.existsBy(com.jrobertgardzinski.email.domain.NormalizedEmail.of(NEW)))
                .thenReturn(true);

        assertInstanceOf(ConfirmEmailChange.Result.EmailTaken.class, confirmEmailChange.execute(TOKEN));
        // the stores that follow the account are touched only once the move is known to be possible
        Mockito.verify(userRepository, Mockito.never()).updateEmail(Mockito.any(), Mockito.any());
        Mockito.verifyNoInteractions(enrolledFactorRepository, sessionRepository);
    }

    @Example
    @Label("A registration that wins the race is still answered honestly, not as a broken database")
    void refuses_when_the_repository_loses_the_race() {
        Mockito.when(mailedLinkRepository.confirmChange(TOKEN)).thenReturn(Optional.of(fresh(new EmailChange(OLD, NEW))));
        Mockito.doThrow(new com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException())
                .when(userRepository).updateEmail(OLD, NEW);

        assertInstanceOf(ConfirmEmailChange.Result.EmailTaken.class, confirmEmailChange.execute(TOKEN));
        // the account did not move, so nothing that follows it may have moved either
        Mockito.verifyNoInteractions(enrolledFactorRepository, sessionRepository);
        Mockito.verify(mailedLinkRepository, Mockito.never()).purge(Mockito.any());
        Mockito.verify(userRepository, Mockito.never()).markVerified(Mockito.any());
    }

    @Example
    @Label("Sessions minted for the old address are revoked: a session cannot follow the account")
    void sessions_do_not_survive_the_move() {
        Mockito.when(mailedLinkRepository.confirmChange(TOKEN)).thenReturn(Optional.of(fresh(new EmailChange(OLD, NEW))));

        assertInstanceOf(ConfirmEmailChange.Result.EmailChanged.class, confirmEmailChange.execute(TOKEN));
        // a session remembers only the address, so one left alive keeps authorizing as OLD — and
        // starts resolving to whoever registers OLD next
        Mockito.verify(sessionRepository).revokeAllSessions(OLD);
    }

    @Example
    @Label("A change confirmed while the account is being deleted is refused, not applied")
    void refuses_while_the_account_is_being_deleted() {
        Mockito.when(mailedLinkRepository.confirmChange(TOKEN)).thenReturn(Optional.of(fresh(new EmailChange(OLD, NEW))));
        Mockito.when(userRepository.isPendingDeletion(OLD)).thenReturn(true);

        assertInstanceOf(ConfirmEmailChange.Result.AccountClosing.class, confirmEmailChange.execute(TOKEN));
        // moving a locked user would hide them from both ends of the saga: the compensation that
        // unlocks them and the completion that deletes them both look under the old address
        Mockito.verify(userRepository, Mockito.never()).updateEmail(OLD, NEW);
        Mockito.verifyNoInteractions(enrolledFactorRepository);
    }

    @Example
    @Label("What belongs to the account follows it: factors, recovery codes, the passwordless mark, federated links")
    void what_belongs_to_the_account_follows_it() {
        // Every one of these tables is keyed by the address with no foreign key to cascade. Left
        // behind, a lookup under the new address finds nothing: the second factor disappears without
        // a trace and a password alone signs in again, the printed recovery codes stop working, a
        // federated account reads as "has a password" (which locks its owner out of deleting it),
        // and the provider's identity opens nothing. Checked on state, against the fakes: one call
        // per repository stands for several things now, and counting calls cannot see what a call
        // forgot.
        java.time.Clock systemClock = java.time.Clock.systemUTC();
        var users = new com.jrobertgardzinski.security.domain.repository.FakeUserRepository(systemClock);
        var factors = new com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository();
        var links = new com.jrobertgardzinski.security.domain.repository.FakeMailedLinkRepository(systemClock);
        users.save(new com.jrobertgardzinski.security.domain.entity.User(
                OLD, new com.jrobertgardzinski.password.domain.HashedPassword("hash")));
        users.markVerified(OLD);
        users.setPasswordless(OLD, true);
        users.linkFederatedIdentity("google", "subject-1", OLD);
        factors.enrol(new com.jrobertgardzinski.security.domain.entity.EnrolledFactor(
                OLD, com.jrobertgardzinski.security.domain.vo.FactorType.EMAIL_CODE, "e-mail code", 2, OLD.value()));
        factors.replaceRecoveryCodes(OLD, java.util.List.of("hash-1", "hash-2"));
        links.startChange(new EmailChange(OLD, NEW), TOKEN);

        new ConfirmEmailChange(links, users, factors, sessionRepository,
                java.time.Duration.ofMinutes(TOKEN_TTL_MINUTES), systemClock).execute(TOKEN);

        assertEquals(java.util.Optional.of(NEW), users.findEmailByFederatedIdentity("google", "subject-1"));
        assertEquals(true, users.isPasswordless(NEW));
        assertEquals(false, users.isPasswordless(OLD));
        assertEquals(1, factors.findByUser(NEW).size());
        // a code factor that targeted the account's own address is re-targeted too
        assertEquals(NEW.value(), factors.findByUser(NEW).get(0).secretMaterial());
        assertEquals(0, factors.findByUser(OLD).size());
        assertEquals(2, factors.unusedRecoveryCodes(NEW));
        assertEquals(0, factors.unusedRecoveryCodes(OLD));
    }

    @Example
    @Label("What was e-mailed to the old address is dropped, and its verification is forgotten")
    void what_was_mailed_to_the_old_address_is_dropped() {
        // A reset link e-mailed before the move is matched by ADDRESS. Left pending, it would set the
        // password of whoever registers the freed address next — the same takeover the deletion path
        // had, reached through an e-mail change instead. A "verified" row left under the freed
        // address would vouch for that stranger as well.
        java.time.Clock systemClock = java.time.Clock.systemUTC();
        var users = new com.jrobertgardzinski.security.domain.repository.FakeUserRepository(systemClock);
        var links = new com.jrobertgardzinski.security.domain.repository.FakeMailedLinkRepository(systemClock);
        users.save(new com.jrobertgardzinski.security.domain.entity.User(
                OLD, new com.jrobertgardzinski.password.domain.HashedPassword("hash")));
        users.markVerified(OLD);
        links.startReset(OLD, new com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken("reset-token"));
        links.startChange(new EmailChange(OLD, Email.of("another@example.com")), new VerificationToken("older-change"));
        links.startChange(new EmailChange(OLD, NEW), TOKEN);

        new ConfirmEmailChange(links, users, enrolledFactorRepository, sessionRepository,
                java.time.Duration.ofMinutes(TOKEN_TTL_MINUTES), systemClock).execute(TOKEN);

        assertEquals(false, links.hasPendingResetFor(OLD));
        assertEquals(false, links.hasPendingChangeNaming(OLD));
        assertEquals(false, users.isVerified(OLD));
        assertEquals(true, users.isVerified(NEW));
    }

    @Example
    @Label("An unknown token is rejected and no email is changed")
    void unknown_token_is_rejected() {
        Mockito.when(mailedLinkRepository.confirmChange(TOKEN)).thenReturn(Optional.empty());

        assertInstanceOf(ConfirmEmailChange.Result.TokenNotFound.class, confirmEmailChange.execute(TOKEN));
        Mockito.verify(userRepository, Mockito.never()).updateEmail(Mockito.any(), Mockito.any());
        Mockito.verify(enrolledFactorRepository, Mockito.never()).reassign(Mockito.any(), Mockito.any());
        Mockito.verify(mailedLinkRepository, Mockito.never()).purge(Mockito.any());
    }

    /** A ticket issued a minute ago — well inside the window every example here assumes. */
    private MailedLinkRepository.PendingEmailChange fresh(EmailChange change) {
        return new MailedLinkRepository.PendingEmailChange(change, NOW.minusMinutes(1));
    }

    @Example
    @Label("A ticket older than the window is refused as expired; the edge answers it like an unknown one")
    void an_expired_ticket_is_refused() {
        // It MOVES the account: one sitting unnoticed in an old mailbox must stop working, the same
        // way a password-reset link does. It did not, until the moment it was issued was stored.
        Mockito.when(mailedLinkRepository.confirmChange(TOKEN)).thenReturn(Optional.of(
                new MailedLinkRepository.PendingEmailChange(new EmailChange(OLD, NEW),
                        NOW.minusMinutes(TOKEN_TTL_MINUTES + 1))));

        assertEquals(new ConfirmEmailChange.Result.TokenExpired(), confirmEmailChange.execute(TOKEN));
        Mockito.verify(userRepository, Mockito.never()).updateEmail(OLD, NEW);
    }
}
