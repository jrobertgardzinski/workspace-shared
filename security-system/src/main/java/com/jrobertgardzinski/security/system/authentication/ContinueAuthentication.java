package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.system.mfa.PendingAuthentication;
import com.jrobertgardzinski.security.system.mfa.PresentProof;

import java.util.Optional;

/**
 * Drives a sign-in that is past link #1 through its remaining factors, one proof at a time. Lives
 * in the authentication package so it can mint the session through the same {@link _GenerateSession}
 * the password path uses — the session is identical however the chain was walked. A wrong proof
 * costs an attempt AND is written down against the same pair a wrong password is — this address
 * against this account, which is why the chain remembers where it began;
 * running out, or an unknown/expired ticket, ends the attempt.
 */
public class ContinueAuthentication {

    private final PresentProof presentProof;
    private final _GenerateSession generateSession;
    private final _AccountStillSignsIn accountStillSignsIn;
    private final _UpdateBruteForceRecords updateBruteForceRecords;

    ContinueAuthentication(
                    PresentProof presentProof,
                    _GenerateSession generateSession,
                    _AccountStillSignsIn accountStillSignsIn,
                    _UpdateBruteForceRecords updateBruteForceRecords) {
        this.presentProof = presentProof;
        this.generateSession = generateSession;
        this.accountStillSignsIn = accountStillSignsIn;
        this.updateBruteForceRecords = updateBruteForceRecords;
    }

    /**
     * The outcome of presenting a factor proof against a sign-in in flight: the chain completed (here
     * is the session), another factor is now due, the proof was wrong (this many tries left), too many
     * wrong proofs tore the ticket down, or the ticket is unknown/expired.
     */
    public sealed interface Result {

        record Completed(SessionTokens session) implements Result {}

        record NextFactor(FactorType type, String challengeData) implements Result {}

        record WrongProof(int attemptsLeft) implements Result {}

        record TooManyAttempts() implements Result {}

        record InvalidTicket() implements Result {}
    }

    public Result execute(String ticket, String proof) {

        PresentProof.Result presentProofResult = presentProof.present(ticket, proof);

        PendingAuthentication chain = presentProofResult.chain();

        switch (presentProofResult.status()) {
            case INVALID_TICKET -> { return new Result.InvalidTicket(); }
            case TOO_MANY_ATTEMPTS -> {
                writeDownWrongProof(chain);
                return new Result.TooManyAttempts();
            }
            case WRONG_PROOF -> {
                writeDownWrongProof(chain);
                return new Result.WrongProof(
                        chain.attemptsLeft());
            }
            case PASSED -> {}
        }

        if (
                presentProofResult.chainCompleted()
        ) {
            // the chain proved the PERSON; whether the ACCOUNT still signs in is a separate question,
            // and link #1's answer to it is as old as the chain took to walk. A deletion requested
            // after the password step used to be answered with a brand-new session.
            if (
                    !accountStillSignsIn.isTrueOf(chain.email())
            ) {
                return new Result.InvalidTicket();
            }

            return new Result.Completed(
                    generateSession.create(chain.email()));
        }

        return new Result.NextFactor(
                chain.currentType(),
                chain.challengeData());
    }

    /**
     * A wrong proof is a wrong guess at this account from this address, so it is written down like
     * a wrong password. Five-per-ticket was never a limit on the person: link #1 is a password they
     * already hold, so a new ticket costs one request and the codes could be walked at whatever
     * rate tickets could be minted.
     */
    private void writeDownWrongProof(PendingAuthentication chain) {

        Optional<LockoutSubject> lockoutSubject = chain.lockoutSubject();

        if (
                lockoutSubject.isPresent()
        ) {
            updateBruteForceRecords.execute(
                    lockoutSubject.get());
        }
    }
}
