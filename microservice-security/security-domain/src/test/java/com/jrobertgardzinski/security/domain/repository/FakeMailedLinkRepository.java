package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link MailedLinkRepository}: one pending reset per address and pending moves keyed by
 * their ticket, each consumed once.
 *
 * <p>It keys by the token OBJECT and not by a hash of it. Hashing is how an adapter avoids keeping
 * a live credential in a store it does not control; nothing in this process outlives the test
 * method, and the port promises single use, not a storage format. A fake that hashed would be
 * imitating the adapter's precaution rather than its promise.
 *
 * <p>{@link #purge} drops a pending change that names this address at EITHER end, the same as both
 * real adapters: a move still pending for an address the account no longer owns is a live ticket
 * pointing at a stranger's account.
 */
public final class FakeMailedLinkRepository implements MailedLinkRepository {

    private record Reset(PasswordResetToken token, LocalDateTime requestedAt) {}

    private final Map<String, Reset> resetsByEmail = new HashMap<>();
    private final Map<VerificationToken, PendingEmailChange> changesByToken = new HashMap<>();
    private final Clock clock;

    public FakeMailedLinkRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void startReset(Email email, PasswordResetToken token) {
        resetsByEmail.put(email.value(), new Reset(token, LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingReset> consumeReset(PasswordResetToken token) {
        for (Map.Entry<String, Reset> pending : resetsByEmail.entrySet()) {
            if (pending.getValue().token().equals(token)) {
                resetsByEmail.remove(pending.getKey());
                return Optional.of(new PendingReset(Email.of(pending.getKey()),
                        pending.getValue().requestedAt()));
            }
        }
        return Optional.empty();
    }

    @Override
    public void startChange(EmailChange change, VerificationToken token) {
        changesByToken.put(token, new PendingEmailChange(change, LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingEmailChange> confirmChange(VerificationToken token) {
        return Optional.ofNullable(changesByToken.remove(token));
    }

    @Override
    public void purge(Email email) {
        resetsByEmail.remove(email.value());
        changesByToken.values().removeIf(pending -> email.equals(pending.change().currentEmail())
                || email.equals(pending.change().newEmail()));
    }

    /** Whether a reset link issued for this address would still be redeemable. */
    public boolean hasPendingResetFor(Email email) {
        return resetsByEmail.containsKey(email.value());
    }

    /** Whether any pending move still names this address, as either end of it. */
    public boolean hasPendingChangeNaming(Email email) {
        return changesByToken.values().stream().anyMatch(pending ->
                email.equals(pending.change().currentEmail())
                        || email.equals(pending.change().newEmail()));
    }
}
