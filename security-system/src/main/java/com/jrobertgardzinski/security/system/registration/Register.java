package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.email.policy.AdmitEmail;
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
         * Both channels at once: the rules the address broke and the rules the password broke, at least
         * one list non-empty, and the policy the password was judged against, because that policy can
         * change between attempts; the address policy is a boot value the edge already holds.
         */
        record Rejected(List<EmailError> emailErrors, List<PasswordError> passwordErrors,
                        PasswordPolicy policy) implements Result {
            public Rejected {
                emailErrors = List.copyOf(emailErrors);
                passwordErrors = List.copyOf(passwordErrors);
            }
        }

        record EmailAlreadyTaken(Email email) implements Result {}
    }

    public Result execute(Supplier<Email> emailCandidate, Supplier<PlaintextPassword> passwordCandidate) {

        AdmitEmail.Result admitEmailResult = admitEmail.evaluate(emailCandidate);

        // judged even when the address already failed: the form shows both channels in one round.
        // A passing password is hashed here, BEFORE existsBy below: a taken address answers the same
        // 201 as a fresh one, and it must take the same time, or the clock tells the caller what
        // the body hides
        CreatePasswordHash.Result createPasswordHashResult = createPasswordHash.create(passwordCandidate);

        if (
                !admitEmailResult.errors().isEmpty() ||
                !createPasswordHashResult.errors().isEmpty()
        ) {
            return new Result.Rejected(
                    admitEmailResult.errors(),
                    createPasswordHashResult.errors(),
                    createPasswordHashResult.policy());
        }

        if (
                userRepository.existsBy(
                        NormalizedEmail.of(
                                admitEmailResult.email()))
        ) {
            return new Result.EmailAlreadyTaken(
                    admitEmailResult.email());
        }

        try {
            return new Result.Registered(
                    userRepository.save(
                            new User(
                                    admitEmailResult.email(),
                                    createPasswordHashResult.hashedPassword())));
        } catch (EmailAlreadyTakenException e) {
            // the storage uniqueness check lost the race after our existsBy check passed
            return new Result.EmailAlreadyTaken(
                    admitEmailResult.email());
        }
    }
}
