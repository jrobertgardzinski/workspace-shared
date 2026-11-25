package com.jrobertgardzinski.security.application.admin;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.mfa.SessionElevation;
import com.jrobertgardzinski.security.system.roles.RequireRole;

/**
 * The bridge for an administrator's levers over other people's accounts. Strings in, plain values
 * out.
 *
 * <p>Every lever has the same two gates, in the same order. The caller must be an ADMIN, from a
 * persisted grant or the deployment's bootstrap list: a non-admin learns only that they are not
 * one. And the caller must have just stepped up for this very action, because a lever pulled on a
 * stolen admin session outlives the session. What the request names is read between the two, so a
 * typo does not cost the one-shot elevation.
 */
public final class AdminService {

    private final RequireRole requireRole;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final SessionElevation sessionElevation;
    private final TransactionBoundary transactionBoundary;

    public AdminService(
                    RequireRole requireRole,
                    EnrolledFactorRepository enrolledFactorRepository,
                    SessionElevation sessionElevation,
                    TransactionBoundary transactionBoundary) {
        this.requireRole = requireRole;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.sessionElevation = sessionElevation;
        this.transactionBoundary = transactionBoundary;
    }

    public sealed interface ResetFactorsOutcome {

        /** Every factor of the account is gone; {@code user} is the address as it was asked about. */
        record FactorsReset(String user) implements ResetFactorsOutcome {}

        record NotAnAdmin() implements ResetFactorsOutcome {}

        /** Not an address the domain can construct. */
        record InvalidEmail() implements ResetFactorsOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements ResetFactorsOutcome {}
    }

    /**
     * The recovery path when someone is locked out of every factor (a lost phone): wipes them, so
     * the account drops below its role floor and has to enrol again. The recovery codes stay.
     * {@code accessToken} is the bearer token as sent, or null when there was none.
     */
    public ResetFactorsOutcome resetFactors(String callerEmail, String accessToken, String userEmail) {

        Caller caller = callerOf(callerEmail);

        if (
                !isAdmin(caller)
        ) {
            return new ResetFactorsOutcome.NotAnAdmin();
        }

        Email user;
        try {
            user = Email.of(userEmail);
        } catch (IllegalArgumentException invalid) {
            return new ResetFactorsOutcome.InvalidEmail();
        }

        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.ADMIN_RESET)
        ) {
            return new ResetFactorsOutcome.StepUpRequired(
                    StepUpAction.ADMIN_RESET.wire());
        }

        transactionBoundary.execute(() -> {
            enrolledFactorRepository.removeAll(user);
            return null;
        });

        return new ResetFactorsOutcome.FactorsReset(userEmail);
    }

    /** Asked about ADMIN only, so whichever refusal comes back, the caller is not one. */
    private boolean isAdmin(Caller caller) {
        try {
            requireRole.check(caller, Role.ADMIN);
            return true;
        } catch (RequireRole.NotAUser | RequireRole.NotAModerator | RequireRole.NotAnAdmin refused) {
            return false;
        }
    }

    private static Caller callerOf(String callerEmail) {
        return new Caller(
                Email.of(callerEmail));
    }
}
