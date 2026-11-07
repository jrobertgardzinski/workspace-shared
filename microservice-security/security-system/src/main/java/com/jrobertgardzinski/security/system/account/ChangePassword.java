package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
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
 * meet the policy. The caller's identity comes from their access token, so only the email is needed.
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

        record UserNotFound() implements Result {}

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
                    Email email,
                    Supplier<PlaintextPassword> currentPasswordCandidate,
                    Supplier<PlaintextPassword> newPasswordCandidate) {

        VerifyPassword.Result verifyPasswordResult = verifyPassword.verify(email, currentPasswordCandidate);

        switch (verifyPasswordResult.status()) {
            case CredentialsStatus.UserNotFound _ -> { return new Result.UserNotFound(); }
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
