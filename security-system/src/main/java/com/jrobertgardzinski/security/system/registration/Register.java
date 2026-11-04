package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.email.policy.AdmitEmail;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

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

        AdmitEmail.Result adminEmailResult = admitEmail.evaluate(emailCandidate);

        // judged even when the address already failed: the form shows both channels in one round.
        // A passing password is hashed here, BEFORE existsBy below: a taken address answers the same
        // 201 as a fresh one, and it must take the same time, or the clock tells the caller what
        // the body hides
        CreatePasswordHash.Result createPasswordHashResult = createPasswordHash.create(passwordCandidate);

        if (!adminEmailResult.errors().isEmpty() || !createPasswordHashResult.errors().isEmpty()) {
            return new RegisterResult.Rejected(adminEmailResult.errors(), createPasswordHashResult.errors(), createPasswordHashResult.policy());
        }
        if (userRepository.existsBy(NormalizedEmail.of(adminEmailResult.email()))) {
            return new RegisterResult.EmailAlreadyTaken(adminEmailResult.email());
        }
        try {
            return new RegisterResult.Registered(userRepository.save(new User(adminEmailResult.email(), createPasswordHashResult.hashedPassword())));
        } catch (EmailAlreadyTakenException e) {
            // the storage uniqueness check lost the race after our existsBy check passed
            return new RegisterResult.EmailAlreadyTaken(adminEmailResult.email());
        }
    }
}
