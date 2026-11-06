package com.jrobertgardzinski.security.system.password;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.CredentialsStatus;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class VerifyPassword {

    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithm;

    /**
     * The hash an address with no account is checked against.
     *
     * <p>Without it, a check for an unknown address skipped the Argon2 verify entirely and came
     * back in about a millisecond, while a known address cost the full hash — so the answer "no
     * such account here" was readable from the clock, on endpoints whose whole vocabulary was built
     * to avoid saying it. Hashing a value nobody will ever type gives the absent case the same work
     * to do; it is computed once, when this is built, against whatever algorithm the deployment
     * wired.
     */
    private final HashedPassword absentAccountHash;

    public VerifyPassword(
                    UserRepository userRepository,
                    HashAlgorithmPort hashAlgorithm) {
        this.userRepository = userRepository;
        this.hashAlgorithm = hashAlgorithm;
        this.absentAccountHash = hashAlgorithm.hash(PlaintextPassword.of(UUID.randomUUID().toString()));
    }

    /**
     * The account, present only when the password {@code PASSED} ({@code null} otherwise), and
     * which of the three it was.
     */
    public record Result(User user, CredentialsStatus status) {}

    /** A candidate that does not construct (blank) is no password at all, so it is a wrong one. */
    public Result verify(Email email, Supplier<PlaintextPassword> passwordCandidate) {

        PlaintextPassword plaintextPassword;
        try {
            plaintextPassword = passwordCandidate.get();
        } catch (IllegalArgumentException blank) {
            return new Result(null, CredentialsStatus.WRONG_PASSWORD);
        }

        return verify(email, plaintextPassword);
    }

    public Result verify(Email email, PlaintextPassword plaintextPassword) {

        Optional<User> user = userRepository.findBy(email);

        HashedPassword passwordHash = absentAccountHash;
        if (
                user.isPresent()
        ) {
            passwordHash = user.get().passwordHash();
        }

        // always verify SOMETHING: the branch that decides is taken after the work, not instead of it
        boolean passwordMatches = hashAlgorithm.verify(passwordHash, plaintextPassword);

        if (
                user.isEmpty()
        ) {
            return new Result(null, CredentialsStatus.USER_NOT_FOUND);
        }

        if (
                !passwordMatches
        ) {
            return new Result(null, CredentialsStatus.WRONG_PASSWORD);
        }

        return new Result(user.get(), CredentialsStatus.PASSED);
    }
}
