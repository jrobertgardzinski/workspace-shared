package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeEnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FakeMailedLinkRepository;
import com.jrobertgardzinski.security.domain.repository.FakeUserRepository;
import com.jrobertgardzinski.security.domain.repository.MailedLinkRepository;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Use case")
@Feature("Delete account")
class DeleteAccountTest {

    private static final Email EMAIL = Email.of("user@example.com");

    private UserRepository userRepository;
    private SessionRepository sessionRepository;
    private EnrolledFactorRepository enrolledFactorRepository;
    private MailedLinkRepository mailedLinkRepository;
    private DeleteAccount deleteAccount;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        sessionRepository = Mockito.mock(SessionRepository.class);
        enrolledFactorRepository = Mockito.mock(EnrolledFactorRepository.class);
        mailedLinkRepository = Mockito.mock(MailedLinkRepository.class);
        deleteAccount = new DeleteAccount(userRepository, sessionRepository,
                enrolledFactorRepository, mailedLinkRepository);
    }

    @Example
    @Label("Closing the account revokes sessions, wipes second factors and pending links, then deletes the user")
    void wipes_everything_then_deletes_the_user() {
        deleteAccount.execute(EMAIL);

        // the secrets (factor material, recovery-code hashes) and the live tokens must be gone
        // BEFORE the user row is, so a crash mid-way can never leave them behind with no account
        // pointing at them. The teardown of the factors is purge, not the admin reset removeAll:
        // removeAll leaves the recovery codes.
        InOrder inOrder = Mockito.inOrder(sessionRepository, enrolledFactorRepository,
                mailedLinkRepository, userRepository);
        inOrder.verify(sessionRepository).revokeAllSessions(EMAIL);
        inOrder.verify(enrolledFactorRepository).purge(EMAIL);
        inOrder.verify(mailedLinkRepository).purge(EMAIL);
        inOrder.verify(userRepository).deleteByEmail(EMAIL);
    }

    @Example
    @Label("Nothing keyed by the address survives the account: every store answers as if it never existed")
    void nothing_keyed_by_the_address_survives() {
        // An earlier version of this test claimed "nothing of the account survives" in a COMMENT,
        // while four tables keyed by the address really did outlive the account, and a reset link
        // left in one of them set the password of whoever registered the freed address next. The
        // promise is checked on state, against the fakes, because one call per repository now
        // stands for several things: the test that only counts calls cannot see what a call forgot.
        Clock clock = Clock.systemUTC();
        FakeUserRepository users = new FakeUserRepository(clock);
        FakeEnrolledFactorRepository factors = new FakeEnrolledFactorRepository();
        FakeMailedLinkRepository links = new FakeMailedLinkRepository(clock);
        Email elsewhere = Email.of("elsewhere@example.com");

        users.save(new User(EMAIL, new HashedPassword("hash")));
        users.markVerified(EMAIL);
        users.setPasswordless(EMAIL, true);
        users.linkFederatedIdentity("google", "subject-1", EMAIL);
        factors.enrol(new EnrolledFactor(EMAIL, FactorType.EMAIL_CODE, "e-mail code", 2, EMAIL.value()));
        factors.replaceRecoveryCodes(EMAIL, List.of("hash-1", "hash-2"));
        links.startReset(EMAIL, new PasswordResetToken("reset-token"));
        links.startChange(new EmailChange(EMAIL, elsewhere), new VerificationToken("change-token"));

        new DeleteAccount(users, sessionRepository, factors, links).execute(EMAIL);

        assertTrue(users.findBy(EMAIL).isEmpty(), "the account");
        assertFalse(users.isVerified(EMAIL), "the verified flag");
        assertFalse(users.isPasswordless(EMAIL), "the passwordless mark");
        assertEquals(0, users.countLinksOf(EMAIL), "the federated links");
        assertTrue(users.findEmailByFederatedIdentity("google", "subject-1").isEmpty(), "the federated identity");
        assertTrue(factors.findByUser(EMAIL).isEmpty(), "the factors");
        assertEquals(0, factors.unusedRecoveryCodes(EMAIL), "the recovery codes");
        assertFalse(links.hasPendingResetFor(EMAIL), "the pending reset");
        assertFalse(links.hasPendingChangeNaming(EMAIL), "the pending change");
    }
}
