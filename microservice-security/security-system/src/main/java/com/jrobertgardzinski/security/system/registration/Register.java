package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.email.policy.AdmitEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.List;
import java.util.function.Supplier;

public class Register {

    private final UserRepository userRepository;
    private final AdmitEmail admitEmail;
    private final CreatePasswordHash createPasswordHash;

    public Register(
                    UserRepository userRepository,
                    AdmitEmail admitEmail,
                    CreatePasswordHash createPasswordHash) {
        this.userRepository = userRepository;
        this.admitEmail = admitEmail;
        this.createPasswordHash = createPasswordHash;
    }

    public sealed interface Result {

        record Registered(User user) implements Result {}

        /**
         * The address broke the e-mail policy and the password passed: the rules the address broke,
         * in policy order. The address policy is a boot value the edge already holds.
         */
        record EmailRejected(List<EmailError> emailErrors) implements Result {
            public EmailRejected {
                emailErrors = List.copyOf(emailErrors);
            }
        }

        /**
         * The address passed and the password broke the policy: the rules it broke, and the policy
         * it was judged against, because that policy can change between attempts.
         */
        record PasswordRejected(List<PasswordError> passwordErrors, PasswordPolicy policy) implements Result {
            public PasswordRejected {
                passwordErrors = List.copyOf(passwordErrors);
            }
        }

        /** Both channels at once, so the form shows both in one round. */
        record BothRejected(List<EmailError> emailErrors, List<PasswordError> passwordErrors,
                            PasswordPolicy policy) implements Result {
            public BothRejected {
                emailErrors = List.copyOf(emailErrors);
                passwordErrors = List.copyOf(passwordErrors);
            }
        }

        record EmailAlreadyTaken(Email email) implements Result {}
    }

    public Result execute(Supplier<Email> emailCandidate, Supplier<PlaintextPassword> passwordCandidate) {

        Email email;
        try {
            email = admitEmail.evaluate(emailCandidate);
        } catch (AdmitEmail.Rejected emailRejected) {
            // the password is judged even when the address already failed: the form shows both
            // channels in one round
            try {
                createPasswordHash.create(passwordCandidate);
            } catch (CreatePasswordHash.Rejected passwordRejected) {
                return new Result.BothRejected(
                        emailRejected.errors(),
                        passwordRejected.errors(),
                        passwordRejected.policy());
            }
            return new Result.EmailRejected(
                    emailRejected.errors());
        }

        // A passing password is hashed here, BEFORE existsBy below: a taken address answers the same
        // 201 as a fresh one, and it must take the same time, or the clock tells the caller what
        // the body hides
        HashedPassword hashedPassword;
        try {
            hashedPassword = createPasswordHash.create(passwordCandidate);
        } catch (CreatePasswordHash.Rejected passwordRejected) {
            return new Result.PasswordRejected(
                    passwordRejected.errors(),
                    passwordRejected.policy());
        }

        if (
                userRepository.existsBy(
                        NormalizedEmail.of(email))
        ) {
            return new Result.EmailAlreadyTaken(email);
        }

        try {
            return new Result.Registered(
                    userRepository.save(
                            new User(email, hashedPassword)));
        } catch (EmailAlreadyTakenException e) {
            // the storage uniqueness check lost the race after our existsBy check passed
            return new Result.EmailAlreadyTaken(email);
        }
    }
}
