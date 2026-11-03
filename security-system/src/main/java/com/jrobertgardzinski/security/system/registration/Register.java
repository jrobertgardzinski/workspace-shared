package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.email.domain.InvalidEmailException;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.email.policy.CanRegister;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.password.policy.PasswordPolicyInForce;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class Register {

    private final UserRepository userRepository;
    private final CanRegisterConfig canRegisterConfig;
    private final CanRegister canRegister;
    private final CreatePasswordHash createPasswordHash;
    private final PasswordPolicyInForce passwordPolicyInForce;

    public Register(UserRepository userRepository, CanRegisterConfig canRegisterConfig,
                    HashAlgorithmPort hashAlgorithm, PasswordPolicyInForce passwordPolicyInForce) {
        this.userRepository = userRepository;
        this.canRegisterConfig = canRegisterConfig;
        this.canRegister = new CanRegister();
        this.createPasswordHash = new CreatePasswordHash(hashAlgorithm);
        this.passwordPolicyInForce = passwordPolicyInForce;
    }

    public RegisterResult execute(Supplier<Email> emailCandidate, Supplier<PlaintextPassword> passwordCandidate) {
        PasswordPolicy passwordPolicy = passwordPolicyInForce.current();

        Email email = null;
        List<EmailError> emailErrors = List.of();
        try {
            email = emailCandidate.get();
        } catch (InvalidEmailException refused) {
            emailErrors = List.of(refused.error());
        }
        if (email != null) {
            switch (canRegister.evaluate(email, canRegisterConfig)) {
                case CanRegister.Result.Success success -> { }
                case CanRegister.Result.Failure failure -> emailErrors = failure.errors();
            }
        }

        // judged even when the address already failed: the form shows both channels in one round.
        // A passing password is hashed here, BEFORE existsBy below: a taken address answers the same
        // 201 as a fresh one, and it must take the same time, or the clock tells the caller what
        // the body hides
        PlaintextPassword password = null;
        CreatePasswordHash.Result.Failure passwordFailure = null;
        try {
            password = passwordCandidate.get();
        } catch (IllegalArgumentException blank) {
            passwordFailure = new CreatePasswordHash.Result.Failure(List.of(PasswordError.PASSWORD_BLANK), passwordPolicy);
        }
        HashedPassword hashedPassword = null;
        if (password != null) {
            switch (createPasswordHash.create(password, passwordPolicy)) {
                case CreatePasswordHash.Result.Success success -> hashedPassword = success.hashedPassword();
                case CreatePasswordHash.Result.Failure failure -> passwordFailure = failure;
            }
        }

        if (!emailErrors.isEmpty() || passwordFailure != null) {
            return new RegisterResult.Rejected(emailErrors, Optional.ofNullable(passwordFailure));
        }
        if (userRepository.existsBy(NormalizedEmail.of(email))) {
            return new RegisterResult.EmailAlreadyTaken(email);
        }
        try {
            return new RegisterResult.Registered(userRepository.save(new User(email, hashedPassword)));
        } catch (EmailAlreadyTakenException e) {
            // the storage uniqueness check lost the race after our existsBy check passed
            return new RegisterResult.EmailAlreadyTaken(email);
        }
    }
}
