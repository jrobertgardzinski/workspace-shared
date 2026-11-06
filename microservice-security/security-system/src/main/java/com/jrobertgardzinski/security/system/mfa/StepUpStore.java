package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.email.domain.Email;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Step-ups in flight, keyed by a one-shot ticket: who is stepping up, which session (access token)
 * to elevate on success, and the factor chain being walked. Short-lived, in memory — like the
 * sign-in pending store, a lost entry only means starting the step-up over.
 */
public interface StepUpStore {

    record StepUpPending(Email email, String accessToken, StepUpAction action, PendingAuthentication chain) {}

    String open(StepUpPending pending);

    Optional<StepUpPending> find(String ticket);

    void replace(String ticket, StepUpPending pending);

    /**
     * Read, change and write back as ONE step — the same reason as
     * {@link PendingAuthenticationStore#update}: the attempt counter is the cap on guessing a
     * factor, and spending it across three calls let concurrent proofs share one attempt.
     */
    default Optional<StepUpPending> update(String ticket, UnaryOperator<StepUpPending> change) {
        Optional<StepUpPending> current = find(ticket);
        current.map(change).ifPresent(next -> replace(ticket, next));
        return current.map(change);
    }

    void close(String ticket);

    /**
     * This store seen as the chains behind its tickets, for {@link PresentProof}. A change to a
     * chain goes through {@link #update}, so it stays one step and keeps the session and the action
     * the ticket was opened for.
     */
    default ChainTickets chains() {
        StepUpStore store = this;
        return new ChainTickets() {

            @Override
            public Optional<PendingAuthentication> find(String ticket) {
                return store.find(ticket).map(StepUpPending::chain);
            }

            @Override
            public Optional<PendingAuthentication> update(String ticket, UnaryOperator<PendingAuthentication> change) {
                return store.update(ticket, current -> new StepUpPending(
                                current.email(), current.accessToken(), current.action(),
                                change.apply(current.chain())))
                        .map(StepUpPending::chain);
            }

            @Override
            public void replace(String ticket, PendingAuthentication chain) {
                store.update(ticket, current -> new StepUpPending(
                        current.email(), current.accessToken(), current.action(), chain));
            }

            @Override
            public void close(String ticket) {
                store.close(ticket);
            }
        };
    }
}
