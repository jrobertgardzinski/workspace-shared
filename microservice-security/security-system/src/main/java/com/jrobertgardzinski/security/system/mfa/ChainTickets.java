package com.jrobertgardzinski.security.system.mfa;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The factor chain behind a ticket, and nothing else a ticket may carry: what {@link PresentProof}
 * needs of a ticket store. A sign-in's store holds exactly this; a step-up's holds it next to the
 * session and the action it elevates, and hands this view of itself.
 */
public interface ChainTickets {

    Optional<PendingAuthentication> find(String ticket);

    /** Read, change and write back as ONE step, returning what is now stored (empty if the ticket is gone). */
    Optional<PendingAuthentication> update(String ticket, UnaryOperator<PendingAuthentication> change);

    void replace(String ticket, PendingAuthentication chain);

    void close(String ticket);
}
