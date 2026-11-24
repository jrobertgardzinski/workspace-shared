package com.jrobertgardzinski.security.domain.repository;

import java.time.LocalDateTime;
import java.time.Clock;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Keyed by email value (string) so lookups don't depend on Email's identity. */
public final class FakeUserRepository implements UserRepository {

    private final java.util.Set<String> pendingDeletion = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, User> byEmail = new HashMap<>();
    private final Map<String, User> byNormalizedEmail = new HashMap<>();

    // raw token values as keys (test scope, so no hashing)
    private final Map<String, String> pendingTokenByEmail = new HashMap<>();
    private final Map<String, LocalDateTime> requestedAtByEmail = new HashMap<>();
    private final Map<String, Boolean> verifiedByEmail = new HashMap<>();
    private final java.util.Set<String> passwordless = new java.util.HashSet<>();
    /** Insertion-ordered so the links answer in the order they were made. */
    private final Map<String, String> emailByFederatedIdentity = new java.util.LinkedHashMap<>();
    private final Clock clock;

    public FakeUserRepository(Clock clock) {
        this.clock = clock;
    }

    /** For the tests that do not look at dates. */
    public FakeUserRepository() {
        this(Clock.systemUTC());
    }

    @Override
    public java.util.List<User> findAllBy(java.util.Collection<UserId> ids) {
        return byEmail.values().stream().filter(user -> ids.contains(user.id())).toList();
    }

    @Override
    public Optional<User> findBy(Email email) {
        return Optional.ofNullable(byEmail.get(email.value()));
    }

    @Override
    public boolean existsBy(NormalizedEmail normalizedEmail) {
        return byNormalizedEmail.containsKey(normalizedEmail.value());
    }

    /** Refuses a taken address exactly as both production adapters do (see {@code UserRepository#save}). */
    @Override
    public User save(User user) {
        if (byEmail.containsKey(user.email().value())
                || byNormalizedEmail.containsKey(user.normalizedEmail().value())) {
            throw new com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException();
        }
        byEmail.put(user.email().value(), user);
        byNormalizedEmail.put(user.normalizedEmail().value(), user);
        return user;
    }

    @Override
    public void updatePassword(Email email, com.jrobertgardzinski.password.domain.HashedPassword passwordHash) {
        User existing = byEmail.get(email.value());
        if (existing != null) {
            User updated = new User(existing.id(), existing.email(), passwordHash, existing.normalizedEmail(), existing.roles());
            byEmail.put(email.value(), updated);
            byNormalizedEmail.put(existing.normalizedEmail().value(), updated);
        }
    }

    @Override
    public void updateEmail(Email currentEmail, Email newEmail) {
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
        forgetVerification(currentEmail);
    }

    private void forgetVerification(Email email) {
        pendingTokenByEmail.remove(email.value());
        requestedAtByEmail.remove(email.value());
        verifiedByEmail.remove(email.value());
    }

    @Override
    public void deleteByEmail(Email email) {
        passwordless.remove(email.value());
        emailByFederatedIdentity.values().removeIf(email.value()::equals);
        forgetVerification(email);
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
    public void startVerification(Email email, VerificationToken token) {
        pendingTokenByEmail.put(email.value(), token.value());
        requestedAtByEmail.put(email.value(), LocalDateTime.now(clock));
        verifiedByEmail.put(email.value(), false);
    }

    @Override
    public Optional<PendingVerification> completeVerification(VerificationToken token) {
        return pendingTokenByEmail.entrySet().stream()
                .filter(e -> e.getValue().equals(token.value()))
                .findFirst()
                .map(e -> {
                    verifiedByEmail.put(e.getKey(), true);
                    pendingTokenByEmail.remove(e.getKey());
                    return new PendingVerification(
                            Email.of(e.getKey()), requestedAtByEmail.remove(e.getKey()));
                });
    }

    @Override
    public void markVerified(Email email) {
        pendingTokenByEmail.remove(email.value());
        requestedAtByEmail.remove(email.value());
        verifiedByEmail.put(email.value(), true);
    }

    @Override
    public boolean isVerified(Email email) {
        return verifiedByEmail.getOrDefault(email.value(), false);
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

    /** How many identities still open this account — the reader a closure asserts on. */
    public int countLinksOf(Email userEmail) {
        return (int) emailByFederatedIdentity.values().stream().filter(userEmail.value()::equals).count();
    }
}
