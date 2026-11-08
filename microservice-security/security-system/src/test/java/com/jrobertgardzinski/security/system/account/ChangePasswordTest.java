package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.system.password.VerifyPassword;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import net.jqwik.api.Example;
import net.jqwik.api.Label;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Epic("Use case")
@Feature("Change password")
class ChangePasswordTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final Caller CALLER = new Caller(EMAIL);
    private static final Supplier<PlaintextPassword> CURRENT = () -> PlaintextPassword.of("OldPassword1!");
    private static final Supplier<PlaintextPassword> NEW_STRONG = () -> PlaintextPassword.of("NewPassword1!");
    private static final Supplier<PlaintextPassword> NEW_WEAK = () -> PlaintextPassword.of("weak");
    private static final Supplier<PlaintextPassword> NEW_BLANK = () -> PlaintextPassword.of("   ");
    private static final PasswordPolicy POLICY = PasswordPolicy.withDefaults();

    private static final HashAlgorithmPort FAKE_ALGORITHM = new HashAlgorithmPort() {
        @Override
        public HashedPassword hash(PlaintextPassword plaintextPassword) {
            return new HashedPassword("hash:" + plaintextPassword.value());
        }

        @Override
        public boolean verify(HashedPassword hashedPassword, PlaintextPassword plaintextPassword) {
            return hashedPassword.value().equals("hash:" + plaintextPassword.value());
        }
    };

    private UserRepository userRepository;
    private SessionRepository sessions;
    private ChangePassword changePassword;

    @BeforeTry
    void init() {
        userRepository = Mockito.mock(UserRepository.class);
        sessions = Mockito.mock(SessionRepository.class);
        Mockito.when(userRepository.findBy(EMAIL)).thenReturn(Optional.of(
                new User(EMAIL, new HashedPassword("hash:OldPassword1!"))));
        changePassword = new ChangePassword(
                userRepository,
                new VerifyPassword(userRepository, FAKE_ALGORITHM),
                new CreatePasswordHash(FAKE_ALGORITHM, () -> POLICY),
                sessions);
    }

    @Example
    @Label("Correct current password and a strong new one change the password")
    void changes_the_password() {
        assertInstanceOf(ChangePassword.Result.Changed.class, changePassword.execute(CALLER, CURRENT, NEW_STRONG));

        Mockito.verify(userRepository).updatePassword(EMAIL, new HashedPassword("hash:NewPassword1!"));
    }

    @Example
    @Label("A changed password revokes every session the old one left behind")
    void a_change_revokes_every_existing_session() {
        // Changing the password is what a user does when they suspect somebody else is on their
        // account. Sessions are rows unrelated to the password hash, so replacing the hash alone
        // left the thief's stolen token authorizing — and rotating itself on /refresh — for the
        // whole refresh window.
        assertInstanceOf(ChangePassword.Result.Changed.class, changePassword.execute(CALLER, CURRENT, NEW_STRONG));

        Mockito.verify(sessions).revokeAllSessions(EMAIL);
    }

    @Example
    @Label("A caller whose account has gone from under the session answers AccountGone and nothing changes")
    void a_deleted_account_is_account_gone() {
        Mockito.when(userRepository.findBy(EMAIL)).thenReturn(Optional.empty());

        assertInstanceOf(ChangePassword.Result.AccountGone.class,
                changePassword.execute(CALLER, CURRENT, NEW_STRONG));

        Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any());
        Mockito.verify(sessions, Mockito.never()).revokeAllSessions(Mockito.any());
    }

    @Example
    @Label("A wrong current password answers WrongPassword and nothing changes")
    void wrong_current_password_is_wrong_password() {
        Supplier<PlaintextPassword> wrong = () -> PlaintextPassword.of("WrongPassword1!");

        assertInstanceOf(ChangePassword.Result.WrongPassword.class,
                changePassword.execute(CALLER, wrong, NEW_STRONG));

        Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any());
        Mockito.verify(sessions, Mockito.never()).revokeAllSessions(Mockito.any());
    }

    @Example
    @Label("A wrong current password is answered before the new one is judged")
    void wrong_current_password_wins_over_a_weak_new_one() {
        Supplier<PlaintextPassword> wrong = () -> PlaintextPassword.of("WrongPassword1!");

        assertInstanceOf(ChangePassword.Result.WrongPassword.class,
                changePassword.execute(CALLER, wrong, NEW_WEAK));
    }

    @Example
    @Label("A new password that breaks the policy is rejected, each rule by its code, naming the policy in force, and nothing changes")
    void weak_new_password_is_rejected() {
        ChangePassword.Result result = changePassword.execute(CALLER, CURRENT, NEW_WEAK);

        ChangePassword.Result.Rejected rejected = assertInstanceOf(ChangePassword.Result.Rejected.class, result);
        assertAll(
                () -> assertEquals(List.of(PasswordError.MIN_LENGTH_NOT_MET, PasswordError.UPPERCASE_REQUIRED,
                                PasswordError.DIGIT_REQUIRED, PasswordError.SPECIAL_CHAR_REQUIRED),
                        rejected.passwordErrors()),
                () -> assertEquals(POLICY, rejected.policy()),
                () -> Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any()),
                () -> Mockito.verify(sessions, Mockito.never()).revokeAllSessions(Mockito.any())
        );
    }

    @Example
    @Label("A blank new password is a code of its own, not an exception, and nothing changes")
    void blank_new_password_is_a_code() {
        ChangePassword.Result result = changePassword.execute(CALLER, CURRENT, NEW_BLANK);

        ChangePassword.Result.Rejected rejected = assertInstanceOf(ChangePassword.Result.Rejected.class, result);
        assertAll(
                () -> assertEquals(List.of(PasswordError.PASSWORD_BLANK), rejected.passwordErrors()),
                () -> Mockito.verify(userRepository, Mockito.never()).updatePassword(Mockito.any(), Mockito.any())
        );
    }
}
