package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.email.policy.AdmitEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

import java.util.Optional;
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

    public RegisterResult execute(Supplier<Email> emailCandidate, Supplier<PlaintextPassword> passwordCandidate) {

        Email email = null;
        AdmitEmail.Result.Failure emailFailure = null;
        switch (admitEmail.evaluate(emailCandidate)) {
            case AdmitEmail.Result.Success success -> email = success.email();
            case AdmitEmail.Result.Failure failure -> emailFailure = failure;
        }

        // judged even when the address already failed: the form shows both channels in one round.
        // A passing password is hashed here, BEFORE existsBy below: a taken address answers the same
        // 201 as a fresh one, and it must take the same time, or the clock tells the caller what
        // the body hides
        HashedPassword hashedPassword = null;
        CreatePasswordHash.Result.Failure passwordFailure = null;
        switch (createPasswordHash.create(passwordCandidate)) {
            case CreatePasswordHash.Result.Success success -> hashedPassword = success.hashedPassword();
            case CreatePasswordHash.Result.Failure failure -> passwordFailure = failure;
        }

        if (emailFailure != null || passwordFailure != null) {
            return new RegisterResult.Rejected(Optional.ofNullable(emailFailure), Optional.ofNullable(passwordFailure));
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
