package com.jrobertgardzinski;

import java.time.LocalDateTime;
import java.time.Clock;
import jakarta.inject.Inject;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link UserRepository} used when no database is configured (tests). The JDBC adapter
 * takes over once a datasource is present. Keyed by the string value of the email so lookups don't
 * depend on {@code Email} identity; a second index on the normalized email backs registration
 * deduplication, so provider aliases (Gmail dots / {@code +tags}) of the same address count as taken.
 */
@Singleton
@Requires(missingBeans = DataSource.class)
public final class InMemoryUserRepository implements UserRepository {

    private final java.util.Set<String> pendingDeletion = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, User> byEmail = new ConcurrentHashMap<>();
    private final Map<String, User> byNormalizedEmail = new ConcurrentHashMap<>();

    private record Verification(String pendingTokenHash, boolean verified, LocalDateTime requestedAt) {}

    private final Map<String, Verification> verificationByEmail = new ConcurrentHashMap<>();
    private final java.util.Set<String> passwordless = ConcurrentHashMap.newKeySet();
    /** provider|subject -> the account's address */
    private final Map<String, String> emailByFederatedIdentity = new ConcurrentHashMap<>();
    private final Clock clock;

    @Inject
    public InMemoryUserRepository(Clock clock) {
        this.clock = clock;
    }

    /** For tests that build the adapter by hand and do not look at dates. */
    public InMemoryUserRepository() {
        this(Clock.systemUTC());
    }

    @Override
    public java.util.List<User> findAllBy(java.util.Collection<UserId> ids) {
        return byEmail.values().stream().filter(user -> ids.contains(user.id())).toList();
    }

    @Override
    public Optional<User> findBy(Email email) {
        // by the normalized form, like the JDBC twin: the index the database keeps is the identity
        return Optional.ofNullable(byNormalizedEmail.get(NormalizedEmail.of(email).value()));
    }

    @Override
    public boolean existsBy(NormalizedEmail normalizedEmail) {
        return byNormalizedEmail.containsKey(normalizedEmail.value());
    }

    /**
     * The same contract the JDBC adapter has: a taken address is refused, never overwritten.
     * Both indexes are checked because both columns are UNIQUE in the schema — an alias of a
     * taken address (Gmail dots, {@code +tags}) is taken too. {@code synchronized} makes the
     * check-and-put one step, which is what the database's unique constraint does for the other
     * adapter; without it two concurrent registrations of one address could both get through.
     */
    @Override
    public synchronized User save(User user) {
        if (byEmail.containsKey(user.email().value())
                || byNormalizedEmail.containsKey(user.normalizedEmail().value())) {
            throw new EmailAlreadyTakenException();
        }
        byEmail.put(user.email().value(), user);
        byNormalizedEmail.put(user.normalizedEmail().value(), user);
        return user;
    }

    @Override
    public void setRoles(Email email, java.util.Set<Role> roles) {
        User existing = byEmail.get(email.value());
        if (existing != null) {
            User updated = new User(existing.id(), existing.email(), existing.passwordHash(),
                    existing.normalizedEmail(), roles);
            byEmail.put(email.value(), updated);
            byNormalizedEmail.put(updated.normalizedEmail().value(), updated);
        }
    }

    @Override
    public int countAdmins() {
        return (int) byEmail.values().stream().filter(user -> user.hasRole(Role.ADMIN)).count();
    }

    @Override
    public void updatePassword(com.jrobertgardzinski.email.domain.Email email,
                               com.jrobertgardzinski.password.domain.HashedPassword passwordHash) {
        User existing = byEmail.get(email.value());
        if (existing != null) {
            User updated = new User(existing.id(), existing.email(), passwordHash, existing.normalizedEmail(), existing.roles());
            byEmail.put(email.value(), updated);
            byNormalizedEmail.put(existing.normalizedEmail().value(), updated);
        }
    }

    @Override
    public void updateEmail(Email currentEmail, Email newEmail) {
        // the index the database has, kept by hand: moving onto a taken address must refuse rather
        // than replace whoever is there — the adapter without a datasource is production wiring too.
        // Uniqueness is by the NORMALIZED form, the same rule save is held to; moving an account
        // onto an address it already holds is not a collision with anybody.
        User occupant = byNormalizedEmail.get(NormalizedEmail.of(newEmail).value());
        if (occupant != null && !occupant.email().value().equals(currentEmail.value())) {
            throw new EmailAlreadyTakenException();
        }
        User existing = byEmail.remove(currentEmail.value());
        if (existing != null) {
            byNormalizedEmail.remove(existing.normalizedEmail().value());
            User moved = new User(existing.id(), newEmail, existing.passwordHash(), NormalizedEmail.of(newEmail), existing.roles());
            byEmail.put(newEmail.value(), moved);
            byNormalizedEmail.put(moved.normalizedEmail().value(), moved);
        }
        if (passwordless.remove(currentEmail.value())) {
            passwordless.add(newEmail.value());
        }
        emailByFederatedIdentity.replaceAll(
                (key, email) -> currentEmail.value().equals(email) ? newEmail.value() : email);
        verificationByEmail.remove(currentEmail.value());
    }

    @Override
    public void deleteByEmail(Email email) {
        passwordless.remove(email.value());
        emailByFederatedIdentity.values().removeIf(email.value()::equals);
        verificationByEmail.remove(email.value());
        User removed = byEmail.remove(email.value());
        if (removed != null) {
            byNormalizedEmail.remove(removed.normalizedEmail().value());
        }
        pendingDeletion.remove(email.value());
    }

    @Override
    public void markPendingDeletion(Email email) {
        pendingDeletion.add(email.value());
    }

    @Override
    public void clearPendingDeletion(Email email) {
        pendingDeletion.remove(email.value());
    }

    @Override
    public boolean isPendingDeletion(Email email) {
        return pendingDeletion.contains(email.value());
    }

    @Override
    public void startVerification(Email email, VerificationToken token) {
        verificationByEmail.put(email.value(),
                new Verification(TokenHashing.hash(token), false, LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingVerification> completeVerification(VerificationToken token) {
        String hash = TokenHashing.hash(token);
        return verificationByEmail.entrySet().stream()
                .filter(e -> hash.equals(e.getValue().pendingTokenHash()))
                .findFirst()
                .map(e -> {
                    LocalDateTime requestedAt = e.getValue().requestedAt();
                    verificationByEmail.put(e.getKey(), new Verification(null, true, requestedAt));
                    return new PendingVerification(Email.of(e.getKey()), requestedAt);
                });
    }

    @Override
    public void markVerified(Email email) {
        verificationByEmail.put(email.value(), new Verification(null, true, LocalDateTime.now(clock)));
    }

    @Override
    public boolean isVerified(Email email) {
        Verification verification = verificationByEmail.get(email.value());
        return verification != null && verification.verified();
    }

    @Override
    public boolean isPasswordless(Email email) {
        return passwordless.contains(email.value());
    }

    @Override
    public void setPasswordless(Email email, boolean value) {
        if (value) {
            passwordless.add(email.value());
        } else {
            passwordless.remove(email.value());
        }
    }

    @Override
    public Optional<Email> findEmailByFederatedIdentity(String provider, String subject) {
        return Optional.ofNullable(emailByFederatedIdentity.get(provider + "|" + subject)).map(Email::of);
    }

    @Override
    public void linkFederatedIdentity(String provider, String subject, Email userEmail) {
        emailByFederatedIdentity.put(provider + "|" + subject, userEmail.value());
    }
}
