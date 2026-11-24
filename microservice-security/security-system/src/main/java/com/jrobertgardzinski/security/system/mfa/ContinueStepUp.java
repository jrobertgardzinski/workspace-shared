package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Optional;

/**
 * The second half of a step-up: a factor proof presented against the ticket {@link StepUp} opened.
 * The chain is walked exactly as a sign-in's is; what completes is an elevation of the session the
 * ticket was opened for, not a new session.
 */
public class ContinueStepUp {

    private final PresentProof presentProof;
    private final StepUpStore store;
    private final SessionElevation elevation;

    public ContinueStepUp(
                    PresentProof presentProof,
                    StepUpStore store,
                    SessionElevation elevation) {
        this.presentProof = presentProof;
        this.store = store;
        this.elevation = elevation;
    }

    public sealed interface Result {

        record Elevated() implements Result {}

        record FactorRequired(String ticket, FactorType nextFactor, String challengeData) implements Result {}

        record WrongProof(int attemptsLeft) implements Result {}

        record TooManyAttempts() implements Result {}

        record InvalidTicket() implements Result {}
    }

    public Result execute(String ticket, String proof) {

        // the session and the action the ticket was opened for, read BEFORE the proof is judged:
        // a chain that completes closes its ticket
        Optional<StepUpStore.StepUpPending> stepUpPending = store.find(ticket);

        PresentProof.Passed passed;
        try {
            passed = presentProof.present(ticket, proof);
        } catch (PresentProof.InvalidTicket invalidTicket) {
            return new Result.InvalidTicket();
        } catch (PresentProof.TooManyAttempts tooManyAttempts) {
            return new Result.TooManyAttempts();
        } catch (PresentProof.WrongProof wrongProof) {
            return new Result.WrongProof(
                    wrongProof.chain().attemptsLeft());
        }

        if (
                stepUpPending.isEmpty()
        ) {
            return new Result.InvalidTicket();
        }

        if (
                passed.chainCompleted()
        ) {
            elevation.elevate(
                    stepUpPending.get().accessToken(),
                    stepUpPending.get().action());
            return new Result.Elevated();
        }

        return new Result.FactorRequired(
                ticket,
                passed.chain().currentType(),
                passed.chain().challengeData());
    }
}
