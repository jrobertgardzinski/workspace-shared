package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.TokenHashing;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.MailedLinkRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PostgreSQL-backed {@link MailedLinkRepository}, over the {@code password_resets} and
 * {@code email_changes} tables. A pending token is stored as a SHA-256 hash (see
 * {@link TokenHashing}); presenting it matches on that hash, deletes the row (single-use) and
 * returns what was pending. Raw tokens are never stored.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcMailedLinkRepository implements MailedLinkRepository {

    private final PasswordResetJdbcRepository passwordResets;
    private final EmailChangeJdbcRepository emailChanges;
    private final Clock clock;

    JdbcMailedLinkRepository(PasswordResetJdbcRepository passwordResets,
                             EmailChangeJdbcRepository emailChanges,
                             Clock clock) {
        this.passwordResets = passwordResets;
        this.emailChanges = emailChanges;
        this.clock = clock;
    }

    @Override
    public void startReset(Email email, PasswordResetToken token) {
        passwordResets.deleteById(email.value());   // re-requesting reissues a fresh token
        passwordResets.save(new PasswordResetEntity(email.value(), TokenHashing.hash(token),
                LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingReset> consumeReset(PasswordResetToken token) {
        String hash = TokenHashing.hash(token);
        // the DELETE decides, not the read: whoever removes the row consumed the link, and a second
        // presentation of it removes nothing and is told nothing
        return passwordResets.findByTokenHash(hash)
                .filter(entity -> passwordResets.consume(hash) > 0)
                .map(entity -> new PendingReset(Email.of(entity.email()), entity.requestedAt()));
    }

    @Override
    public void startChange(EmailChange change, VerificationToken token) {
        emailChanges.save(new EmailChangeEntity(
                TokenHashing.hash(token), change.currentEmail().value(), change.newEmail().value(),
                LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingEmailChange> confirmChange(VerificationToken token) {
        String hash = TokenHashing.hash(token);
        return emailChanges.findById(hash)
                .filter(entity -> emailChanges.consume(hash) > 0)   // the DELETE decides, not the read
                .map(entity -> new PendingEmailChange(
                        new EmailChange(Email.of(entity.currentEmail()), Email.of(entity.newEmail())),
                        entity.startedAt()));
    }

    /** Both ends of a move: the address can appear as the source of one change and the target of another. */
    @Override
    public void purge(Email email) {
        passwordResets.deleteById(email.value());
        emailChanges.deleteByCurrentEmail(email.value());
        emailChanges.deleteByNewEmail(email.value());
    }
}
