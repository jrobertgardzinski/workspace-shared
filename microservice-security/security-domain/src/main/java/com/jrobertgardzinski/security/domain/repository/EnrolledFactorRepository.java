package com.jrobertgardzinski.security.domain.repository;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.List;

/**
 * The factors each user has enrolled — the per-user half of the MFA configuration (the deployment
 * half is which adapters are enabled). Ordered by the chain position the user chose.
 *
 * <p>The user's recovery codes live here too: single-use stand-ins for any chain link they lost
 * access to. They are not an entity of their own, only a list of hashes that belongs to the same
 * person's second factors; the plain codes exist once, at generation, on the user's screen.
 */
public interface EnrolledFactorRepository {

    /** A user's enrolled factors, ordered by their chain position. */
    List<EnrolledFactor> findByUser(Email userEmail);

    /** Add or replace (same user + type) an enrolled factor. */
    void enrol(EnrolledFactor factor);

    /** Remove one factor; a no-op if it was not enrolled. */
    void remove(Email userEmail, FactorType type);

    /** Remove every factor a user has — an admin reset when someone is locked out of all of them. */
    void removeAll(Email userEmail);

    /**
     * Moves every factor and every recovery code of an account to a new address — both tables are
     * keyed by the e-mail, and the e-mail is mutable, so they must FOLLOW the account. Spent codes
     * move as well: the count the UI shows must survive the move, and the break-glass codes the
     * user printed must not stop working the moment they change their address. Without this the second factor
     * vanishes silently on an e-mail change (a lookup under the new address finds nothing, so a
     * password alone signs in) and the old secrets stay readable under an address someone else can
     * register. A code factor whose target is the account's own address is re-targeted too — the
     * target is where the next code is sent.
     */
    void reassign(Email fromEmail, Email toEmail);

    /** Store a fresh batch of recovery code hashes, invalidating whatever the user had before. */
    void replaceRecoveryCodes(Email userEmail, List<String> codeHashes);

    /** Spend the recovery code with this hash: true if it existed unused (it is now used up). */
    boolean consumeRecoveryCode(Email userEmail, String codeHash);

    /** How many recovery codes remain unspent — the UI's "you have N left". */
    int unusedRecoveryCodes(Email userEmail);

    /**
     * Forgets everything held for this address, factors and recovery codes alike — the teardown
     * when the account is deleted. {@link #removeAll} is the narrower admin reset: it leaves the
     * codes, which are what a person locked out of every factor still has.
     */
    void purge(Email userEmail);
}
