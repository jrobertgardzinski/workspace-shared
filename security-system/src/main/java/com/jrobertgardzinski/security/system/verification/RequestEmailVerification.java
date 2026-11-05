package com.jrobertgardzinski.security.system.verification;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

/**
 * Starts e-mail verification: mints a single-use token, remembers it against the address, and
 * e-mails the verification link. Re-requesting while the address is still unverified simply
 * issues a fresh token — a lost mail costs nothing.
 *
 * <p>An ALREADY VERIFIED address is left alone, and that is a security rule, not an optimisation:
 * starting a verification resets the address to unverified, and this use case is reached from a
 * public endpoint that takes any address a stranger types in. Without the guard, one request per
 * victim shuts them out of their own account (sign-in demands a verified address) until they
 * follow a link they never asked for.
 */
public class RequestEmailVerification {

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationNotifier notifier;

    public RequestEmailVerification(
                    EmailVerificationRepository emailVerificationRepository,
                    EmailVerificationNotifier notifier) {
        this.emailVerificationRepository = emailVerificationRepository;
        this.notifier = notifier;
    }

    public sealed interface Result {

        record LinkSent() implements Result {}

        /** Nothing to prove, and nothing to take away. */
        record AlreadyVerified() implements Result {}
    }

    public Result execute(Email email) {

        if (
                emailVerificationRepository.isVerified(email)
        ) {
            return new Result.AlreadyVerified();
        }

        VerificationToken token = VerificationToken.random();
        emailVerificationRepository.startVerification(email, token);
        notifier.sendVerificationLink(email, token);

        return new Result.LinkSent();
    }
}
