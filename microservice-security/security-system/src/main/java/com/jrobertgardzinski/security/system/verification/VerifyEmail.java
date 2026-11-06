package com.jrobertgardzinski.security.system.verification;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Completes e-mail verification: a matching, unused token that is still fresh marks the address
 * verified; an unknown, already-used or EXPIRED token is rejected.
 *
 * <p>An expired link is answered exactly like an unknown one, and is spent either way — the same
 * rule the password reset and the e-mail change follow, for the same reason: telling the difference
 * would tell a stranger that a verification for this address was once started. Re-requesting is one
 * click away and costs the requester a fresh mail, which is the intended way back.
 */
public class VerifyEmail {

    private final EmailVerificationRepository emailVerificationRepository;
    private final Duration tokenTtl;
    private final Clock clock;

    public VerifyEmail(
                    EmailVerificationRepository emailVerificationRepository,
                    Duration tokenTtl,
                    Clock clock) {
        this.emailVerificationRepository = emailVerificationRepository;
        this.tokenTtl = tokenTtl;
        this.clock = clock;
    }

    public sealed interface Result {

        record Verified(Email email) implements Result {}

        record TokenNotFound() implements Result {}

        record TokenExpired() implements Result {}
    }

    public Result execute(VerificationToken token) {

        Optional<EmailVerificationRepository.PendingVerification> pendingVerification =
                emailVerificationRepository.completeVerification(token);

        if (
                pendingVerification.isEmpty()
        ) {
            return new Result.TokenNotFound();
        }

        if (
                pendingVerification.get().expiration(tokenTtl).hasExpired(clock)
        ) {
            // too old; consumed, so it is spent for good
            return new Result.TokenExpired();
        }

        return new Result.Verified(
                pendingVerification.get().email());
    }
}
