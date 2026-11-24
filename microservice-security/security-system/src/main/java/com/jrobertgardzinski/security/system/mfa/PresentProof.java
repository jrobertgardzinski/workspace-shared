package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class PresentProof {

    private final MfaChain mfaChain;
    private final ChainTickets chainTickets;
    private final Clock clock;

    public PresentProof(
                    MfaChain mfaChain,
                    ChainTickets chainTickets,
                    Clock clock) {
        this.mfaChain = mfaChain;
        this.chainTickets = chainTickets;
        this.clock = clock;
    }

    /** No live chain stands behind the ticket: it is unknown, or it has aged past its window. */
    public static final class InvalidTicket extends Exception {}

    /**
     * The proof is wrong and the ticket still has tries left: the chain as it now stands, one try
     * fewer, so the caller can say how many are left and whom to charge the miss to.
     */
    public static final class WrongProof extends Exception {

        private final PendingAuthentication chain;

        public WrongProof(PendingAuthentication chain) {
            this.chain = chain;
        }

        public PendingAuthentication chain() {
            return chain;
        }
    }

    /**
     * The proof is wrong and it was the last try: the ticket is gone. The chain still comes back, so
     * a sign-in knows whom to charge the miss to.
     */
    public static final class TooManyAttempts extends Exception {

        private final PendingAuthentication chain;

        public TooManyAttempts(PendingAuthentication chain) {
            this.chain = chain;
        }

        public PendingAuthentication chain() {
            return chain;
        }
    }

    /** The chain as it stands after a right proof, and whether it has no factor left to ask for. */
    public record Passed(PendingAuthentication chain, boolean chainCompleted) {}

    public Passed present(String ticket, String proof) throws InvalidTicket, WrongProof, TooManyAttempts {

        Optional<PendingAuthentication> pendingAuthentication = chainTickets.find(ticket);

        // an unknown ticket, or one whose chain has aged past its window, ends here. A sign-in
        // always made this check; a step-up used to trust the ticket for ever, which is the kind of
        // drift one shared step exists to end.
        if (
                pendingAuthentication.isEmpty() ||
                pendingAuthentication.get().isExpired(clock)
        ) {
            chainTickets.close(ticket);
            throw new InvalidTicket();
        }

        PendingAuthentication chain = pendingAuthentication.get();

        if (
                !mfaChain.verify(chain, proof)
        ) {
            // spend the attempt as one step and decide on what is NOW stored: reading the count,
            // subtracting one and writing it back as three calls let concurrent proofs share the
            // same attempt, so a ticket worth five guesses answered as many as were sent at once
            Optional<PendingAuthentication> afterWrongProof = chainTickets.update(
                    ticket,
                    PendingAuthentication::afterWrongProof);

            if (
                    afterWrongProof.isEmpty() ||
                    afterWrongProof.get().attemptsLeft() <= 0
            ) {
                chainTickets.close(ticket);
                throw new TooManyAttempts(chain);
            }

            throw new WrongProof(afterWrongProof.get());
        }

        List<EnrolledFactor> tail = chain.tail();

        if (
                tail.isEmpty()
        ) {
            chainTickets.close(ticket);
            return new Passed(chain, true);
        }

        PendingAuthentication advanced = mfaChain.advanceTo(chain, tail);
        chainTickets.replace(ticket, advanced);

        return new Passed(advanced, false);
    }
}
