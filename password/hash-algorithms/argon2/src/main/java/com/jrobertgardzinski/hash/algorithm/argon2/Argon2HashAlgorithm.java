package com.jrobertgardzinski.hash.algorithm.argon2;

import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

public class Argon2HashAlgorithm implements HashAlgorithmPort {

    private static final Argon2 argon2 = Argon2Factory.create();

    private final Argon2Config config;

    public Argon2HashAlgorithm() {
        this(Argon2Config.withDefaults());
    }

    public Argon2HashAlgorithm(Argon2Config config) {
        this.config = config;
    }

    @Override
    public HashedPassword hash(PlaintextPassword plaintextPassword) {
        String hash = argon2.hash(config.iterations().value(), config.memLimit().value(), config.parallelism().value(), plaintextPassword.value().getBytes());
        return new HashedPassword(hash);
    }

    @Override
    public boolean verify(HashedPassword hashedPassword, PlaintextPassword plaintextPassword) {
        return argon2.verify(
                hashedPassword.value(),
                plaintextPassword.value().getBytes()
        );
    }
}
