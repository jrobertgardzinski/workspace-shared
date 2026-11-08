package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.system.password.VerifyPassword;

import java.util.List;
import java.util.function.Supplier;
import com.jrobertgardzinski.security.domain.vo.CredentialsStatus;
import com.jrobertgardzinski.password.domain.PasswordStatus;

/**
 * Changes a signed-in user's password: the current password must match, and the new password must
 * meet the policy. The caller's identity comes from their access token: a {@link Caller}, not an
 * address typed into a form.
 *
 * <p><b>Every session goes with the old password, including the caller's own.</b> Sessions are rows
 * unrelated to the password hash, so replacing the hash used to leave every one of them
 * authorizing — and changing a password is what a user does when they suspect somebody else is on
 * their account. The thief's token kept working, and kept rotating itself on {@code /refresh}, for
 * the whole refresh window. {@code revokeAllSessions} runs inside the caller's transaction, so the
 * new hash and the emptied session table commit together. Logging the caller out too is deliberate:
 * telling one live session from another needs the presented token here, and "sign in again with your
 * new password" is a smaller price than guessing which session is the owner's.
 */
public class ChangePassword {

    private final UserRepository userRepository;
    private final VerifyPassword verifyPassword;
    private final CreatePasswordHash createPasswordHash;
    private final SessionRepository sessionRepository;

    public ChangePassword(
                    UserRepository userRepository,
                    VerifyPassword verifyPassword,
                    CreatePasswordHash createPasswordHash,
                    SessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.verifyPassword = verifyPassword;
        this.createPasswordHash = createPasswordHash;
        this.sessionRepository = sessionRepository;
    }

    public sealed interface Result {

        record Changed() implements Result {}

        /**
         * A live token, and no account under its address in the {@code UserRepository}: the account
         * has gone from under the session, deleted while the token still worked. Used to be
         * {@code UserNotFound}, a copy of the sign-in's vocabulary, where the same status means a
         * stranger guessed an address; here nobody guessed anything, so the name says what happened.
         */
        record AccountGone() implements Result {}

        record WrongPassword() implements Result {}

        /**
         * The rules the new password broke, never empty, and the policy it was judged against,
         * because that policy can change between attempts: the password half of what registration
         * answers with.
         */
        record Rejected(List<PasswordError> passwordErrors, PasswordPolicy policy) implements Result {
            public Rejected {
                passwordErrors = List.copyOf(passwordErrors);
            }
        }
    }

    public Result execute(
                    Caller caller,
                    Supplier<PlaintextPassword> currentPasswordCandidate,
                    Supplier<PlaintextPassword> newPasswordCandidate) {

        Email email = caller.email();

        VerifyPassword.Result verifyPasswordResult = verifyPassword.verify(email, currentPasswordCandidate);

        switch (verifyPasswordResult.status()) {
            case CredentialsStatus.UserNotFound _ -> { return new Result.AccountGone(); }
            case CredentialsStatus.WrongPassword _ -> { return new Result.WrongPassword(); }
            case CredentialsStatus.Passed _ -> {}
        }

        CreatePasswordHash.Result createPasswordHashResult = createPasswordHash.create(newPasswordCandidate);

        switch (createPasswordHashResult.status()) {
            case PasswordStatus.Rejected _ -> {
                return new Result.Rejected(
                        createPasswordHashResult.errors(),
                        createPasswordHashResult.policy());
            }
            case PasswordStatus.Passed _ -> {}
        }

        userRepository.updatePassword(
                email,
                createPasswordHashResult.hashedPassword());

        // the old password's sessions do not survive it
        sessionRepository.revokeAllSessions(email);

        return new Result.Changed();
    }
}
