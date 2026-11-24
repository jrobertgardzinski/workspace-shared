package com.jrobertgardzinski.persistence;

import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.TokenHashing;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EmailAlreadyTakenException;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Role;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.exceptions.DataAccessException;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PostgreSQL-backed {@link UserRepository}: a thin adapter mapping the {@code users} table rows
 * (see {@link UserEntity}) to and from the domain {@link User}, and keeping what else is known
 * about an account in {@code email_verifications}, {@code passwordless_accounts} and
 * {@code federated_identities}. A pending verification token is stored as a SHA-256 hash. Active only when a datasource is
 * present; otherwise the in-memory repository serves.
 */
@Singleton
@Requires(beans = DataSource.class)
final class JdbcUserRepository implements UserRepository {

    private final UserJdbcRepository repository;
    private final EmailVerificationJdbcRepository emailVerifications;
    private final PasswordlessAccountJdbcRepository passwordlessAccounts;
    private final FederatedIdentityJdbcRepository federatedIdentities;
    private final java.time.Clock clock;

    JdbcUserRepository(UserJdbcRepository repository,
                       EmailVerificationJdbcRepository emailVerifications,
                       PasswordlessAccountJdbcRepository passwordlessAccounts,
                       FederatedIdentityJdbcRepository federatedIdentities,
                       java.time.Clock clock) {
        this.repository = repository;
        this.emailVerifications = emailVerifications;
        this.passwordlessAccounts = passwordlessAccounts;
        this.federatedIdentities = federatedIdentities;
        this.clock = clock;
    }

    @Override
    public java.util.List<User> findAllBy(java.util.Collection<com.jrobertgardzinski.identity.UserId> ids) {
        if (ids.isEmpty()) {
            return java.util.List.of();
        }
        return repository.findByIdIn(ids.stream().map(com.jrobertgardzinski.identity.UserId::value).toList())
                .stream().map(JdbcUserRepository::toDomain).toList();
    }

    @Override
    public Optional<User> findBy(Email email) {
        // by the normalized form, because that is the identity the unique index enforces; the
        // spelling somebody typed is not part of who they are
        return repository.findByNormalizedEmail(NormalizedEmail.of(email).value())
                .map(JdbcUserRepository::toDomain);
    }

    @Override
    public boolean existsBy(NormalizedEmail normalizedEmail) {
        return repository.existsByNormalizedEmail(normalizedEmail.value());
    }

    @Override
    public void updatePassword(Email email, HashedPassword passwordHash) {
        repository.updatePassword(email.value(), passwordHash.value());
    }

    @Override
    public void updateEmail(Email currentEmail, Email newEmail) {
        try {
            repository.updateEmail(currentEmail.value(), newEmail.value(), NormalizedEmail.of(newEmail).value());
        } catch (DataAccessException e) {
            if (isUniqueViolation(e)) {
                // the same translation save does: the index is what decides, and what it decided
                // is "somebody else holds this address" — not "the database broke"
                throw new EmailAlreadyTakenException();
            }
            throw e;
        }

        // the passwordless row IS the address (it is the primary key), so a move is a delete plus
        // an insert
        if (passwordlessAccounts.existsById(currentEmail.value())) {
            passwordlessAccounts.deleteById(currentEmail.value());
            if (!passwordlessAccounts.existsById(newEmail.value())) {
                passwordlessAccounts.save(new PasswordlessAccountEntity(newEmail.value()));
            }
        }
        federatedIdentities.repointUserEmail(currentEmail.value(), newEmail.value());
        emailVerifications.deleteById(currentEmail.value());
    }

    @Override
    public void deleteByEmail(Email email) {
        passwordlessAccounts.deleteById(email.value());
        federatedIdentities.deleteByUserEmail(email.value());
        emailVerifications.deleteById(email.value());
        repository.deleteByEmail(email.value());
    }

    @Override
    public void startVerification(Email email, VerificationToken token) {
        emailVerifications.deleteById(email.value());   // re-requesting reissues a fresh token
        emailVerifications.save(new EmailVerificationEntity(email.value(), TokenHashing.hash(token), false,
                java.time.LocalDateTime.now(clock)));
    }

    @Override
    public Optional<PendingVerification> completeVerification(VerificationToken token) {
        String hash = TokenHashing.hash(token);
        return emailVerifications.findByPendingTokenHash(hash)
                .filter(entity -> emailVerifications.markVerified(hash) > 0)   // the UPDATE decides
                .map(entity -> new PendingVerification(Email.of(entity.email()), entity.requestedAt()));
    }

    @Override
    public void markVerified(Email email) {
        emailVerifications.deleteById(email.value());
        emailVerifications.save(new EmailVerificationEntity(email.value(), null, true,
                java.time.LocalDateTime.now(clock)));
    }

    @Override
    public boolean isVerified(Email email) {
        return emailVerifications.findById(email.value()).map(EmailVerificationEntity::verified).orElse(false);
    }

    /** Presence of a row = passwordless. */
    @Override
    public boolean isPasswordless(Email email) {
        return passwordlessAccounts.existsById(email.value());
    }

    @Override
    public void setPasswordless(Email email, boolean value) {
        if (value) {
            if (!passwordlessAccounts.existsById(email.value())) {
                passwordlessAccounts.save(new PasswordlessAccountEntity(email.value()));
            }
        } else {
            passwordlessAccounts.deleteById(email.value());
        }
    }

    @Override
    public Optional<Email> findEmailByFederatedIdentity(String provider, String subject) {
        return federatedIdentities.findById(FederatedIdentityEntity.keyOf(provider, subject))
                .map(entity -> Email.of(entity.userEmail()));
    }

    @Override
    public void linkFederatedIdentity(String provider, String subject, Email userEmail) {
        String key = FederatedIdentityEntity.keyOf(provider, subject);
        federatedIdentities.deleteById(key); // upsert
        federatedIdentities.save(new FederatedIdentityEntity(key, provider, subject,
                userEmail.value(), java.time.LocalDateTime.now(clock)));
    }

    @Override
    public void markPendingDeletion(Email email) {
        repository.setPendingDeletion(email.value(), true);
    }

    @Override
    public void clearPendingDeletion(Email email) {
        repository.setPendingDeletion(email.value(), false);
    }

    @Override
    public boolean isPendingDeletion(Email email) {
        return repository.existsByEmailAndPendingDeletionTrue(email.value());
    }

    @Override
    public User save(User user) {
        try {
            repository.save(new UserEntity(
                    user.id().value(), user.email().value(), user.normalizedEmail().value(), user.passwordHash().value(),
                    false, encodeRoles(user.roles()), java.time.LocalDateTime.now(clock)));
            return user;
        } catch (DataAccessException e) {
            if (isUniqueViolation(e)) {
                throw new EmailAlreadyTakenException();
            }
            throw e;
        }
    }

    @Override
    public void setRoles(Email email, Set<Role> roles) {
        EnumSet<Role> withUser = roles.isEmpty() ? EnumSet.of(Role.USER) : EnumSet.copyOf(roles);
        withUser.add(Role.USER);
        repository.setRoles(email.value(), encodeRoles(withUser));
    }

    @Override
    public int countAdmins() {
        return repository.countAdmins();
    }

    private static String encodeRoles(Set<Role> roles) {
        return roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private static Set<Role> decodeRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of(Role.USER);
        }
        Set<Role> parsed = EnumSet.noneOf(Role.class);
        for (String name : roles.split(",")) {
            try {
                parsed.add(Role.valueOf(name.trim()));
            } catch (IllegalArgumentException unknownRole) {
                // a role dropped from the enum: ignore the stale value rather than fail a login
            }
        }
        parsed.add(Role.USER);
        return parsed;
    }

    /** PostgreSQL SQLSTATE 23505 = unique_violation, here the email / normalized-email constraint. */
    private static boolean isUniqueViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static User toDomain(UserEntity entity) {
        Email email = Email.of(entity.email());
        return new User(new com.jrobertgardzinski.identity.UserId(entity.id()), email, new HashedPassword(entity.passwordHash()),
                NormalizedEmail.of(email), decodeRoles(entity.roles()));
    }
}
