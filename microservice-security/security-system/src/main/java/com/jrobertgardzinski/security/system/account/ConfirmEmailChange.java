package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordResetRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.repository.RecoveryCodeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Completes an email change: a matching, unused token applies the pending change (moving the user
 * to the new address); an unknown or already-used token is rejected. The new address is marked
 * verified — the change token was delivered there, which is the same ownership proof the regular
 * verification flow provides (and sign-in requires a verified address). Federated links FOLLOW the
 * account: they are keyed by the provider's durable subject (the same person, the same Google
 * account), so they are re-pointed at the new address — severing them would orphan the identity,
 * because the provider keeps reporting its own old address and the auto-link at the next sign-in
 * would never find the moved account.
 *
 * <p>A ticket is only good for as long as the window the caller sets: this link MOVES the account,
 * so one sitting unnoticed in an old mailbox must stop working, exactly as a password-reset link
 * does. It did not, until now — nothing was reading the date it was issued, because nothing was
 * stored.
 *
 * <p>Everything else the account owns is keyed by that address too, and not one of those tables has
 * a foreign key to cascade — so this use case moves them by hand or the move loses them. Two kinds,
 * two answers. What belongs to the ACCOUNT follows it: the MFA factors, the recovery codes and the
 * passwordless mark. Left behind, a lookup under the new address finds nothing, so the second factor
 * disappears without a trace and a password alone signs in again; and a federated account reads as
 * "has a password" under its new address, which locks its owner out of ever deleting it. What was
 * E-MAILED TO THE OLD ADDRESS is dropped instead: a pending reset, a pending further change, the
 * verification row. The account no longer owns that mailbox, and a live token pointing at a freed
 * address sets the password of whoever registers it next.
 *
 * <p>SESSIONS are neither moved nor kept: they are revoked, the same price a password change and a
 * password reset already charge. They cannot be moved, because a session remembers only the address
 * it was minted for — so a surviving one keeps authorizing as the OLD address, "sign out everywhere"
 * under the new address never reaches it, and the moment somebody registers the freed address that
 * session starts resolving to THEIR account: their roles, their session list. The owner signs in
 * again after moving; nobody inherits a session by taking over an abandoned address.
 *
 * <p>The address is checked for an occupant before anything moves, and the move itself is still
 * allowed to refuse: the window between requesting a change and following the link is up to a day
 * wide and nothing reserves the target, so somebody may register it in the meantime. Either way the
 * answer is {@link Result.EmailTaken} and nothing has been moved — the stores
 * that follow the account are touched only once the move is known to be possible.
 *
 * <p>A ticket is also refused while the account is being DELETED. The deletion saga locks the
 * account and then waits for other services; a change landing in that window moves the locked user
 * to the new address, where the saga's compensation and its completion can no longer find them —
 * an account locked forever under an address its owner never finished moving to.
 */
public class ConfirmEmailChange {

    private final EmailChangeRepository emailChangeRepository;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordlessAccountRepository passwordlessAccountRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final SessionRepository sessionRepository;
    private final Duration tokenTtl;
    private final Clock clock;

    public ConfirmEmailChange(
                    EmailChangeRepository emailChangeRepository,
                    UserRepository userRepository,
                    EmailVerificationRepository emailVerificationRepository,
                    FederatedIdentityRepository federatedIdentityRepository,
                    EnrolledFactorRepository enrolledFactorRepository,
                    RecoveryCodeRepository recoveryCodeRepository,
                    PasswordlessAccountRepository passwordlessAccountRepository,
                    PasswordResetRepository passwordResetRepository,
                    SessionRepository sessionRepository,
                    Duration tokenTtl,
                    Clock clock) {
        this.emailChangeRepository = emailChangeRepository;
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.federatedIdentityRepository = federatedIdentityRepository;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordlessAccountRepository = passwordlessAccountRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.sessionRepository = sessionRepository;
        this.tokenTtl = tokenTtl;
        this.clock = clock;
    }

    public sealed interface Result {

        /**
         * Both addresses, because the move is announced to the rest of the estate and the
         * announcement is useless without the one the other services' rows are still keyed by.
         */
        record EmailChanged(Email oldEmail, Email newEmail) implements Result {}

        record TokenNotFound() implements Result {}

        record TokenExpired() implements Result {}

        /** The account is being deleted: moving it now would hide it from the deletion saga. */
        record AccountClosing() implements Result {}

        /**
         * Somebody else holds the address now.
         *
         * <p>A change is requested against a free address and confirmed up to a day later, and
         * nothing reserves it in between — so this is an ordinary outcome, not an error, and it is
         * the only one the person can act on ("pick another address"). It is said plainly rather
         * than folded into the token refusals: the token was perfectly good, and telling its owner
         * it was not sends them to request a second one that will fail in exactly the same way.
         *
         * <p>It does tell the confirming user that the address is taken — which is something the
         * quiet request endpoint deliberately does not say. The trade is deliberate too: they are
         * the person who asked to move there, they will find out when they try again, and the
         * alternative is an account stuck in a loop with no way to learn why.
         */
        record EmailTaken() implements Result {}
    }

    public Result execute(VerificationToken token) {

        Optional<EmailChangeRepository.PendingEmailChange> pendingEmailChange =
                emailChangeRepository.confirmChange(token);

        if (
                pendingEmailChange.isEmpty()
        ) {
            return new Result.TokenNotFound();
        }

        if (
                pendingEmailChange.get().expiration(tokenTtl).hasExpired(clock)
        ) {
            // already consumed by the time we look, so it is spent for good either way. The edge
            // answers it exactly like an unknown one: telling the difference on the wire would tell
            // a stranger that a change to this address was once started.
            return new Result.TokenExpired();
        }

        Email currentEmail = pendingEmailChange.get().change().currentEmail();
        Email newEmail = pendingEmailChange.get().change().newEmail();

        if (
                userRepository.isPendingDeletion(currentEmail)
        ) {
            return new Result.AccountClosing();
        }

        if (
                userRepository.existsBy(
                        NormalizedEmail.of(newEmail))
        ) {
            return new Result.EmailTaken();
        }

        federatedIdentityRepository.relinkAll(currentEmail, newEmail);
        enrolledFactorRepository.reassign(currentEmail, newEmail);
        recoveryCodeRepository.reassign(currentEmail, newEmail);
        passwordlessAccountRepository.reassign(currentEmail, newEmail);
        passwordResetRepository.purge(currentEmail);
        emailChangeRepository.purge(currentEmail);
        emailVerificationRepository.purge(currentEmail);

        try {
            userRepository.updateEmail(currentEmail, newEmail);
        } catch (EmailAlreadyTakenException takenSinceWeLooked) {
            // the read above lost a race with a registration; the port's contract is what settles
            // it, and the same answer is owed either way
            return new Result.EmailTaken();
        }

        sessionRepository.revokeAllSessions(currentEmail);
        emailVerificationRepository.markVerified(newEmail);

        return new Result.EmailChanged(
                currentEmail,
                newEmail);
    }
}
