package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.vo.ProofError;

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

    /**
     * The chain as it stands after the proof ({@code null} when no live ticket was found), what was
     * wrong with the proof, and whether the chain has no factor left to ask for.
     */
    public record Result(PendingAuthentication chain, List<ProofError> errors, boolean chainCompleted) {
        public Result {
            errors = List.copyOf(errors);
        }
    }

    public Result present(String ticket, String proof) {

        Optional<PendingAuthentication> pendingAuthentication = chainTickets.find(ticket);

        // an unknown ticket, or one whose chain has aged past its window, ends here. A sign-in
        // always made this check; a step-up used to trust the ticket for ever, which is the kind of
        // drift one shared step exists to end.
        if (
                pendingAuthentication.isEmpty() ||
                pendingAuthentication.get().isExpired(clock)
        ) {
            chainTickets.close(ticket);
            return new Result(null, List.of(ProofError.INVALID_TICKET), false);
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
                return new Result(chain, List.of(ProofError.TOO_MANY_ATTEMPTS), false);
            }

            return new Result(afterWrongProof.get(), List.of(ProofError.WRONG_PROOF), false);
        }

        List<EnrolledFactor> tail = chain.tail();

        if (
                tail.isEmpty()
        ) {
            chainTickets.close(ticket);
            return new Result(chain, List.of(), true);
        }

        PendingAuthentication advanced = mfaChain.advanceTo(chain, tail);
        chainTickets.replace(ticket, advanced);

        return new Result(advanced, List.of(), false);
    }
}
