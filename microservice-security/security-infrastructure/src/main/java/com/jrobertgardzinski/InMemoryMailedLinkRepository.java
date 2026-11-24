package com.jrobertgardzinski;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.MailedLinkRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link MailedLinkRepository} used when no database is configured (tests). Resets are
 * keyed by e-mail, changes by the SHA-256 hash of the pending token; presenting a token removes
 * the entry, so it is single-use. Raw tokens are never stored.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
final class InMemoryMailedLinkRepository implements MailedLinkRepository {

    private record Reset(String tokenHash, LocalDateTime requestedAt) {}

    private final Map<String, Reset> resetsByEmail = new ConcurrentHashMap<>();
    private final Map<String, PendingEmailChange> changesByTokenHash = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryMailedLinkRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void startReset(Email email, PasswordResetToken token) {
        resetsByEmail.put(email.value(), new Reset(TokenHashing.hash(token), LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingReset> consumeReset(PasswordResetToken token) {
        String hash = TokenHashing.hash(token);
        for (Map.Entry<String, Reset> entry : resetsByEmail.entrySet()) {
            if (hash.equals(entry.getValue().tokenHash())) {
                resetsByEmail.remove(entry.getKey());
                return Optional.of(new PendingReset(Email.of(entry.getKey()), entry.getValue().requestedAt()));
            }
        }
        return Optional.empty();
    }

    @Override
    public void startChange(EmailChange change, VerificationToken token) {
        changesByTokenHash.put(TokenHashing.hash(token),
                new PendingEmailChange(change, LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingEmailChange> confirmChange(VerificationToken token) {
        return Optional.ofNullable(changesByTokenHash.remove(TokenHashing.hash(token)));
    }

    /**
     * Drop change tickets nobody confirmed. The twin with a datasource has a reaper doing exactly
     * this; without one here, every abandoned change request stays for the life of the process, and
     * starting one costs a request. RETENTION is deliberately generous — far longer than the window
     * the use case judges by — so that expiry is decided in ONE place and this only sweeps up what
     * could not possibly matter any more. Resets need no sweep: one row per address, overwritten in
     * place.
     */
    @Scheduled(fixedDelay = "1h", initialDelay = "5m")
    void evictAbandoned() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(7);
        changesByTokenHash.values().removeIf(pending -> pending.startedAt().isBefore(cutoff));
    }

    /** Both ends of a move, same as the JDBC adapter. */
    @Override
    public void purge(Email email) {
        resetsByEmail.remove(email.value());
        changesByTokenHash.values().removeIf(pending -> email.equals(pending.change().currentEmail())
                || email.equals(pending.change().newEmail()));
    }
}
