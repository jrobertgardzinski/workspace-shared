package com.jrobertgardzinski.security.domain.repository;

import java.time.LocalDateTime;
import java.time.Duration;
import com.jrobertgardzinski.security.domain.vo.token.expiration.MailedLinkExpiration;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.vo.Role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository {

    /**
     * The account this address belongs to — matched by its NORMALIZED form, which is the same rule
     * {@link #save} refuses duplicates by.
     *
     * <p>The two have to agree, and for a long time they did not: registration refused a second
     * {@code alice@corp.com} once {@code Alice@corp.com} existed, while this lookup answered empty
     * for it — so the person who capitalised their own name on the day they signed up was told
     * "wrong e-mail or password" for ever after, and could not register again either.
     */
    Optional<User> findBy(Email email);

    /** The accounts among these ids that exist; a deleted or unknown id is simply not in the answer. */
    List<User> findAllBy(Collection<UserId> ids);

    /** Replace a user's whole role set (USER is always kept); a no-op if the user is absent. */
    void setRoles(Email email, Set<Role> roles);

    /**
     * How many accounts hold ADMIN right now.
     *
     * <p>Asked before a role change takes ADMIN away: the one question the caller cannot answer for
     * themselves. Granting and revoking roles is itself an admin action, so an administrator who
     * drops their own grant while being the last one leaves a system in which nobody can grant it
     * back — the only way out being the deployment's {@code security.bootstrap-admins}, if it names
     * anybody at all.
     *
     * <p>Bootstrap admins are deliberately NOT counted here: they are admins by CONFIGURATION, not
     * by a row, and whether the deployment declares any is the use case's business to combine with
     * this number.
     */
    int countAdmins();

    /** Replace an existing user's password hash (e.g. after a password reset); a no-op if absent. */
    void updatePassword(Email email, HashedPassword passwordHash);

    /**
     * Move an existing user to a new email (and its normalized form); a no-op if absent.
     *
     * <p>Everything this repository holds under the address moves with the account: the
     * passwordless mark (left behind, a federated account reads as "has a password" under its new
     * address, and a step-up then demands a password the account never had) and every federated
     * link (keyed by the provider's durable subject — the same person, the same Google account —
     * so it follows the account; severing it would orphan the identity, because the provider keeps
     * reporting its own OLD address). The verification of the OLD address does not move, it is
     * forgotten: a "verified" row left under a freed address would vouch for whoever registers it
     * next. Whether the new address is verified is the caller's to say ({@link #markVerified}).
     *
     * <p>The new address is UNIQUE here exactly as it is in {@link #save}, and an attempt to move
     * onto one that is already taken throws {@link EmailAlreadyTakenException} — it never
     * overwrites the account sitting there.
     *
     * <p>Said out loud because the two adapters disagreed, and a change token lives for a day: the
     * address is free when the change is REQUESTED and can be registered by somebody else before
     * the link is followed. With a database the unique index refused and the raw violation escaped
     * as a 500 (the same link then answering 500 until it expired); without one, the in-memory
     * adapter quietly replaced the other person's account with this one. Same call, same input,
     * one answer.
     */
    void updateEmail(Email currentEmail, Email newEmail);

    /**
     * Delete a user by email (close account); a no-op if absent.
     *
     * <p>Everything this repository holds under the address goes with the account, because a freed
     * address can be registered by a stranger: the passwordless mark (it would tell a step-up that
     * the successor's account has no password to check), every federated link (the old owner's
     * provider identity would open the successor's account), and the verification, pending token
     * and verified flag alike (it would vouch for whoever registers the address next).
     */
    void deleteByEmail(Email email);

    /** Lock the account while its deletion saga runs: the user stays but cannot sign in. */
    void markPendingDeletion(Email email);

    /** Roll the deletion lock back (saga compensation): the account works again. */
    void clearPendingDeletion(Email email);

    boolean isPendingDeletion(Email email);

    /**
     * Whether a user already exists under the given normalized email — the identity
     * used for registration deduplication, so provider aliases (e.g. Gmail dots or
     * {@code +tags}) of the same address count as taken.
     */
    boolean existsBy(NormalizedEmail normalizedEmail);

    /**
     * Persist a NEW user. One semantics for every adapter, with or without a database: the
     * (normalized) address is unique, and an attempt to save one that is already taken throws
     * {@link EmailAlreadyTakenException} — it never overwrites the account sitting there.
     *
     * <p>Spelled out because the two adapters used to disagree: the JDBC one translated the unique
     * violation into the exception, the in-memory one (which serves whenever no datasource is
     * configured) silently replaced the existing row. The same registration losing a race therefore
     * ended as "email already taken" with a database and as a stolen account without one.
     */
    User save(User user);

    // ---- the address's verification: a fact about the account, and the mailed token that proves it

    /** Remember (or reset) the pending token e-mailed to this address; the address is not yet verified. */
    void startVerification(Email email, VerificationToken token);

    /**
     * A verification that was started and is still pending: the address it was mailed to, and WHEN.
     *
     * <p>The date is here for the same reason the password reset's is: a link that arrives in a
     * mailbox has to stop working, and the use case is where "how long" is decided. The row used to
     * carry no date at all, so a link from a year ago verified an address as happily as one from a
     * minute ago — which matters most for exactly the addresses nobody is watching.
     */
    record PendingVerification(Email email, LocalDateTime requestedAt) {
        public MailedLinkExpiration expiration(Duration window) {
            return new MailedLinkExpiration(requestedAt, window);
        }
    }

    /**
     * If the token matches a pending verification, mark that address verified and return what was
     * pending; else empty. The token is spent either way — a presented token is a spent token, and
     * whether it was still fresh is the caller's judgement to make. Spending the token and flipping
     * the fact are ONE step, which is why the verification link lives here and not with the other
     * mailed links.
     */
    Optional<PendingVerification> completeVerification(VerificationToken token);

    /**
     * Mark the address verified without a token — for flows that proved ownership by other means
     * (e.g. confirming an email change, whose own token was delivered to that address).
     */
    void markVerified(Email email);

    boolean isVerified(Email email);

    // ---- the account has no usable password

    /**
     * Whether the account has no usable password — one born through a federated (OAuth) sign-in and
     * not since given one. The MFA role floor needs this: a password counts as the first factor, but
     * a provider login does not (a compromised Google account must not, by itself, cover part of an
     * admin's floor). Setting a password (through the reset flow) clears the mark.
     */
    boolean isPasswordless(Email email);

    void setPasswordless(Email email, boolean passwordless);

    // ---- the external identities that open the account

    /**
     * The account a provider's identity opens: one account, many identities — a password, a Google
     * subject, one day a Facebook subject are equal keys to the same user. Keyed by
     * {@code (provider, subject)}, the provider's durable opaque id.
     */
    Optional<Email> findEmailByFederatedIdentity(String provider, String subject);

    /** Linking the same {@code (provider, subject)} again repoints it (upsert). */
    void linkFederatedIdentity(String provider, String subject, Email userEmail);
}
