package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.domain.vo.token.expiration.MailedLinkExpiration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The links this service mails out and waits for: a password reset and a change of address. Each
 * remembers the (hashed) token that was e-mailed; presenting the matching token yields what was
 * pending once, single-use. Raw tokens are never stored.
 *
 * <p>The verification link is not here: whether an address is verified is a fact about the
 * account, and completing a verification flips that fact in the same step that spends the token,
 * so it lives with the account in {@link UserRepository}.
 */
public interface MailedLinkRepository {

    /**
     * A consumed reset: the address it was requested for and WHEN it was requested. The age is part
     * of the answer because single use is not enough on its own — a link that never expires outlives
     * the account it was issued for, and would set the password of whoever registers that address
     * next.
     */
    record PendingReset(Email email, LocalDateTime requestedAt) {
        public MailedLinkExpiration expiration(Duration window) {
            return new MailedLinkExpiration(requestedAt, window);
        }
    }

    /** Remember (or reset) the pending reset token e-mailed to this address. */
    void startReset(Email email, PasswordResetToken token);

    /** If the token matches a pending reset, consume it (single-use) and return it; else empty. */
    Optional<PendingReset> consumeReset(PasswordResetToken token);

    /** A pending move together with the moment it started. */
    record PendingEmailChange(EmailChange change, LocalDateTime startedAt) {
        public MailedLinkExpiration expiration(Duration window) {
            return new MailedLinkExpiration(startedAt, window);
        }
    }

    /** Remember the token e-mailed to the NEW address of a requested move. */
    void startChange(EmailChange change, VerificationToken token);

    /**
     * The ticket, and the moment it was issued — because when it was issued is half the answer.
     *
     * <p>Single use alone is not enough: a link that never expires outlives the reason it was sent.
     * This one MOVES the account, so a stale mail sitting in an old inbox is a way in that survives
     * every later precaution — and it read as valid for months, because nothing was looking at the
     * date. The window itself is a decision, so it is made where decisions are made: in the use
     * case, not here and not in the adapter.
     */
    Optional<PendingEmailChange> confirmChange(VerificationToken token);

    /**
     * Drops every pending link of this address: its reset, and every change that mentions it as
     * EITHER end of a move. Called when the account is deleted and when it moves to another address:
     * a reset link was e-mailed to an address the account no longer owns, a change still pending is
     * a live token pointing at a stranger's account, and the addresses are personal data that must
     * not outlive the account.
     */
    void purge(Email email);
}
