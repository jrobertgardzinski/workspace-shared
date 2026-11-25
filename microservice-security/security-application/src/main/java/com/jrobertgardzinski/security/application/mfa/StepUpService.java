package com.jrobertgardzinski.security.application.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.mfa.ContinueStepUp;
import com.jrobertgardzinski.security.system.mfa.StepUp;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

import java.util.Optional;

/**
 * The bridge for step-up authentication: re-proving yourself for a sensitive action. Starting it
 * decides, per action, whether a password and/or factors are needed; the factor step walks the
 * chain. Passing elevates the caller's access token for a short window, which the sensitive
 * action then spends. Strings in, plain values out.
 *
 * <p>The window is kept per CALLER, not per address. Both steps are authenticated, so the caller's
 * own identity is known and is the honest key: on the address, one NAT shares one budget and a
 * colleague's impatience locks everyone else out of their own accounts. A request with no
 * authenticated caller never gets this far; the edge answers it before anything is counted.
 */
public final class StepUpService {

    private final StepUp stepUp;
    private final ContinueStepUp continueStepUp;
    private final SourceThrottle throttle;

    public StepUpService(
                    StepUp stepUp,
                    ContinueStepUp continueStepUp,
                    SourceThrottle throttle) {
        this.stepUp = stepUp;
        this.continueStepUp = continueStepUp;
        this.throttle = throttle;
    }

    public sealed interface StartOutcome {

        record Elevated() implements StartOutcome {}

        /** The password step is behind; this factor is due against {@code ticket}. */
        record FactorRequired(String ticket, String nextFactor, String challengeData) implements StartOutcome {}

        /** Wrong, missing or blank: one situation, one answer. */
        record WrongPassword() implements StartOutcome {}

        /** A live token, and no account under its address: deleted while the token still worked. */
        record AccountGone() implements StartOutcome {}

        /**
         * Nothing the caller can type would help: the account carries no password and no factor,
         * and this action needs one. The way out is to enrol a factor first.
         */
        record NothingToProveWith() implements StartOutcome {}

        /** Not an action of the catalogue: no elevation is minted for a name nobody would spend. */
        record UnknownAction() implements StartOutcome {}

        /** This caller has spent their window; nothing was judged. */
        record Throttled(long retryAfterSeconds) implements StartOutcome {}
    }

    public sealed interface FactorOutcome {

        record Elevated() implements FactorOutcome {}

        /** The proof passed and another factor is due, against the same ticket. */
        record FactorRequired(String ticket, String nextFactor, String challengeData) implements FactorOutcome {}

        record WrongProof(int attemptsLeft) implements FactorOutcome {}

        /** The ticket ran out of tries and is gone; the step-up starts over. */
        record TooManyAttempts() implements FactorOutcome {}

        record InvalidTicket() implements FactorOutcome {}

        /** The ticket or the proof is missing or blank: nothing to present, nothing to judge. */
        record MissingTicketOrProof() implements FactorOutcome {}

        /** This caller has spent their window; nothing was judged. */
        record Throttled(long retryAfterSeconds) implements FactorOutcome {}
    }

    /**
     * {@code accessToken} is the bearer token as sent: the elevation is minted on it.
     * {@code password} may be null when the caller offered none.
     */
    public StartOutcome start(String callerEmail, String accessToken, String action, String password) {

        Caller caller = callerOf(callerEmail);

        SourceThrottle.Decision decision = throttle.check(
                windowOf(caller));

        if (
                !decision.allowed()
        ) {
            return new StartOutcome.Throttled(
                    decision.retryAfterSeconds());
        }

        Optional<StepUpAction> stepUpAction = StepUpAction.fromWire(action == null ? "" : action);

        if (
                stepUpAction.isEmpty()
        ) {
            return new StartOutcome.UnknownAction();
        }

        // handed over as a candidate: a password that does not construct (absent, blank) is the
        // use case's to judge, and it answers like a wrong one
        StepUp.Result result = stepUp.execute(
                caller,
                stepUpAction.get(),
                accessToken,
                () -> PlaintextPassword.of(password));

        return switch (result) {
            case StepUp.Result.Elevated elevated -> {
                forgive(caller);
                yield new StartOutcome.Elevated();
            }
            case StepUp.Result.FactorRequired factorRequired ->
                    new StartOutcome.FactorRequired(
                            factorRequired.ticket(),
                            factorRequired.nextFactor().value(),
                            factorRequired.challengeData());
            case StepUp.Result.WrongPassword wrongPassword ->
                    new StartOutcome.WrongPassword();
            case StepUp.Result.AccountGone accountGone ->
                    new StartOutcome.AccountGone();
            case StepUp.Result.NothingToProveWith nothingToProveWith ->
                    new StartOutcome.NothingToProveWith();
        };
    }

    public FactorOutcome presentFactor(String callerEmail, String ticket, String proof) {

        Caller caller = callerOf(callerEmail);

        SourceThrottle.Decision decision = throttle.check(
                windowOf(caller));

        if (
                !decision.allowed()
        ) {
            return new FactorOutcome.Throttled(
                    decision.retryAfterSeconds());
        }

        if (
                isMissing(ticket) ||
                isMissing(proof)
        ) {
            return new FactorOutcome.MissingTicketOrProof();
        }

        ContinueStepUp.Result result = continueStepUp.execute(ticket, proof);

        return switch (result) {
            case ContinueStepUp.Result.Elevated elevated -> {
                forgive(caller);
                yield new FactorOutcome.Elevated();
            }
            case ContinueStepUp.Result.FactorRequired factorRequired ->
                    new FactorOutcome.FactorRequired(
                            factorRequired.ticket(),
                            factorRequired.nextFactor().value(),
                            factorRequired.challengeData());
            case ContinueStepUp.Result.WrongProof wrongProof ->
                    new FactorOutcome.WrongProof(
                            wrongProof.attemptsLeft());
            case ContinueStepUp.Result.TooManyAttempts tooManyAttempts ->
                    new FactorOutcome.TooManyAttempts();
            case ContinueStepUp.Result.InvalidTicket invalidTicket ->
                    new FactorOutcome.InvalidTicket();
        };
    }

    /**
     * A caller who has just re-proved themselves is not the volume this window defends against:
     * the same rule the brute-force guard follows on a correct password. Without it a window spent
     * on SUCCESSFUL elevations is the tightest limit in the service, and it lands on the person
     * doing exactly what they are supposed to.
     */
    private void forgive(Caller caller) {
        throttle.forgive(
                windowOf(caller));
    }

    private static String windowOf(Caller caller) {
        return "caller:" + caller.email().value();
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private static Caller callerOf(String callerEmail) {
        return new Caller(
                Email.of(callerEmail));
    }
}
