package com.jrobertgardzinski.password.domain;

/**
 * Hashes a plaintext password, and verifies a plaintext against a stored hash.
 */
public interface HashAlgorithmPort {
    HashedPassword hash(PlaintextPassword plaintextPassword);
    boolean verify(HashedPassword hashedPassword, PlaintextPassword plaintextPassword);
}
