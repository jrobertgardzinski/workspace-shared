package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.security.domain.vo.StepUpRequirement;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.config.mfa.StepUpPolicy;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.system.password.VerifyPassword;

import java.util.List;

/**
 * Re-proves a caller for a sensitive action. The requirement per action comes from the
 * {@link StepUpPolicy}: {@code NONE} elevates at once; {@code FULL_CHAIN} first re-verifies the
 * password (skipped for a passwordless federated account); then both walk the enrolled factors
 * through the shared {@link MfaChain}. Passing the last factor mints a one-shot
 * {@link SessionElevation} on the caller's access token — the sensitive endpoint consumes it.
 * A caller with no factors and no password step is elevated directly (there is nothing extra to
 * prove beyond the live session).
 *
 * <p><b>Except for FULL_CHAIN.</b> A federated account with no password and no enrolled factor has
 * nothing at all to re-prove with, so "step up" meant "hold a live token" — and FULL_CHAIN is the
 * requirement on exactly the actions that must survive a stolen session: deleting the account,
 * moving it to another address, an admin's levers. Those are refused with
 * {@link Result.NothingToProveWith} until the account carries a factor. SECOND_FACTORS actions are
 * deliberately NOT refused: enrolling a factor is one of them, and refusing it would box the caller
 * out of the very act that frees them — the same reasoning {@code AuthorizationFilter} follows for
 * the MFA floor.
 */
public class StepUp {

    private final StepUpPolicy policy;
    private final VerifyPassword verifyPassword;
    private final PasswordlessAccountRepository passwordlessAccountRepository;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final MfaChain chain;
    private final StepUpStore store;
    private final SessionElevation elevation;

    public StepUp(
                    StepUpPolicy policy,
                    VerifyPassword verifyPassword,
                    PasswordlessAccountRepository passwordlessAccountRepository,
                    EnrolledFactorRepository enrolledFactorRepository,
                    MfaChain chain,
                    StepUpStore store,
                    SessionElevation elevation) {
        this.policy = policy;
        this.verifyPassword = verifyPassword;
        this.passwordlessAccountRepository = passwordlessAccountRepository;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.chain = chain;
        this.store = store;
        this.elevation = elevation;
    }

    public sealed interface Result {

        record Elevated() implements Result {}

        record FactorRequired(String ticket, FactorType nextFactor, String challengeData) implements Result {}

        record WrongPassword() implements Result {}

        /**
         * The caller has nothing this action can be proved with: a federated account with no
         * password and no enrolled factor. Not a refusal of the person — a statement that the door
         * cannot be opened until they have a key.
         */
        record NothingToProveWith() implements Result {}
    }

    public Result execute(Email email, StepUpAction action, String accessToken, PlaintextPassword passwordAttempt) {

        StepUpRequirement requirement = policy.requirementFor(action);

        if (
                requirement == StepUpRequirement.NONE
        ) {
            elevation.elevate(accessToken, action);
            return new Result.Elevated();
        }

        List<EnrolledFactor> enrolled = enrolledFactorRepository.findByUser(email);

        if (
                requirement == StepUpRequirement.FULL_CHAIN &&
                enrolled.isEmpty() &&
                passwordlessAccountRepository.isPasswordless(email)
        ) {
            return new Result.NothingToProveWith();
        }

        // The password is proven for FULL_CHAIN, and ALSO when there are no enrolled factors to
        // walk: a requirement other than NONE with an empty factor list used to elevate silently on
        // the live session alone, which turned any non-NONE action into "a stolen token is enough".
        // A passwordless (federated) account has nothing to prove here — its live session is all it
        // ever has — so it keeps the direct elevation.
        boolean mustProvePassword =
                requirement == StepUpRequirement.FULL_CHAIN ||
                enrolled.isEmpty();

        if (
                mustProvePassword &&
                !passwordlessAccountRepository.isPasswordless(email) &&
                !passwordMatches(email, passwordAttempt)
        ) {
            return new Result.WrongPassword();
        }

        if (
                enrolled.isEmpty()
        ) {
            // nothing further to prove
            elevation.elevate(accessToken, action);
            return new Result.Elevated();
        }

        PendingAuthentication pending = chain.begin(email, enrolled);
        String ticket = store.open(
                new StepUpStore.StepUpPending(email, accessToken, action, pending));

        return new Result.FactorRequired(
                ticket,
                enrolled.get(0).type(),
                pending.challengeData());
    }

    /**
     * Null is "no password offered" — the caller sent none, or sent a blank the boundary reads as
     * none — and it answers like a wrong one. The attempt arrives as a {@link PlaintextPassword}
     * because building the domain type out of what came over the wire is the adapter's job, not
     * this use case's.
     */
    private boolean passwordMatches(Email email, PlaintextPassword passwordAttempt) {

        if (
                passwordAttempt == null
        ) {
            return false;
        }

        VerifyPassword.Result verifyPasswordResult = verifyPassword.verify(email, passwordAttempt);

        return verifyPasswordResult.errors().isEmpty();
    }
}
