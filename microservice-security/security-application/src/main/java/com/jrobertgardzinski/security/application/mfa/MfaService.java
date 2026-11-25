package com.jrobertgardzinski.security.application.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.mfa.ConfirmEnrolment;
import com.jrobertgardzinski.security.system.mfa.EnrolFactor;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.GenerateRecoveryCodes;
import com.jrobertgardzinski.security.system.mfa.MfaCompliance;
import com.jrobertgardzinski.security.system.mfa.SessionElevation;

import java.util.List;
import java.util.Set;

/**
 * The bridge for what a signed-in person does with their own second factors: seeing them,
 * enrolling one in two steps, removing one, and minting recovery codes. Strings in, plain values
 * out. Everything here is against the caller's own account.
 *
 * <p>Whatever changes what it takes to sign in later sits behind a fresh step-up, and the gate is
 * here: the service spends the one-shot elevation and answers {@code StepUpRequired} when there
 * was none to spend. What can be refused without it is refused first, so a request that could
 * never succeed does not cost the caller the whole step-up chain.
 */
public final class MfaService {

    private final EnrolFactor enrolFactor;
    private final ConfirmEnrolment confirmEnrolment;
    private final GenerateRecoveryCodes generateRecoveryCodes;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final UserRepository userRepository;
    private final FactorRegistry factorRegistry;
    private final MfaCompliance mfaCompliance;
    private final SessionElevation sessionElevation;
    private final TransactionBoundary transactionBoundary;

    public MfaService(
                    EnrolFactor enrolFactor,
                    ConfirmEnrolment confirmEnrolment,
                    GenerateRecoveryCodes generateRecoveryCodes,
                    EnrolledFactorRepository enrolledFactorRepository,
                    UserRepository userRepository,
                    FactorRegistry factorRegistry,
                    MfaCompliance mfaCompliance,
                    SessionElevation sessionElevation,
                    TransactionBoundary transactionBoundary) {
        this.enrolFactor = enrolFactor;
        this.confirmEnrolment = confirmEnrolment;
        this.generateRecoveryCodes = generateRecoveryCodes;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.userRepository = userRepository;
        this.factorRegistry = factorRegistry;
        this.mfaCompliance = mfaCompliance;
        this.sessionElevation = sessionElevation;
        this.transactionBoundary = transactionBoundary;
    }

    /** One factor the caller has enrolled, as a list shows it. */
    public record Factor(String type, String label) {}

    /** What the caller has, in chain order, and what this deployment offers, by name. */
    public record Factors(List<Factor> have, List<String> offered) {}

    public sealed interface StartEnrolmentOutcome {

        /** A code factor: the code is on its way, there is nothing to show. */
        record CodeSent() implements StartEnrolmentOutcome {}

        /** A possession factor: what to show the person, a TOTP {@code otpauth://} URI. */
        record SetupReady(String display) implements StartEnrolmentOutcome {}

        /** No adapter of this deployment serves the type. */
        record UnsupportedFactor() implements StartEnrolmentOutcome {}

        /** Blank: not a factor type the domain can construct. */
        record UnreadableFactorType() implements StartEnrolmentOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements StartEnrolmentOutcome {}
    }

    public sealed interface ConfirmEnrolmentOutcome {

        record Enrolled(String type) implements ConfirmEnrolmentOutcome {}

        record WrongProof() implements ConfirmEnrolmentOutcome {}

        /** Nothing was started for this type, or it ran out of tries. */
        record NoPendingEnrolment() implements ConfirmEnrolmentOutcome {}

        /** Blank: not a factor type the domain can construct. */
        record UnreadableFactorType() implements ConfirmEnrolmentOutcome {}
    }

    public sealed interface RemoveFactorOutcome {

        record Removed() implements RemoveFactorOutcome {}

        /** The account would drop below the number of factors its roles demand. */
        record WouldBreakFloor() implements RemoveFactorOutcome {}

        /** Blank: not a factor type the domain can construct. */
        record UnreadableFactorType() implements RemoveFactorOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements RemoveFactorOutcome {}
    }

    public sealed interface GenerateRecoveryCodesOutcome {

        /** The plain codes, shown this once: only their hashes are kept. */
        record Generated(List<String> codes) implements GenerateRecoveryCodesOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements GenerateRecoveryCodesOutcome {}
    }

    public Factors factors(String callerEmail) {

        Caller caller = callerOf(callerEmail);

        return new Factors(
                enrolledFactorRepository.findByUser(caller.email()).stream()
                        .map(factor -> new Factor(factor.type().value(), factor.label()))
                        .toList(),
                factorRegistry.offered().stream()
                        .map(FactorType::value)
                        .sorted()
                        .toList());
    }

    /**
     * {@code accessToken} is the bearer token as sent, or null when there was none. {@code target}
     * is where a code factor should send its codes, or null for the caller's own address.
     */
    public StartEnrolmentOutcome startEnrolment(String callerEmail, String accessToken, String type, String target) {

        Caller caller = callerOf(callerEmail);

        // the type is read BEFORE the elevation is spent: the elevation is one-shot, and a typo in
        // the factor type used to cost the whole step-up chain and then answer "unsupported"
        FactorType factorType;
        try {
            factorType = FactorType.of(type);
        } catch (IllegalArgumentException blank) {
            return new StartEnrolmentOutcome.UnreadableFactorType();
        }

        if (
                factorRegistry.forType(factorType).isEmpty()
        ) {
            return new StartEnrolmentOutcome.UnsupportedFactor();
        }

        // enrolling a factor rewrites the sign-in chain, so a merely-live (possibly stolen) session
        // must step up first: otherwise a thief adds a factor they hold and locks the owner out.
        // Guarding the start alone is enough: the confirmation needs a pending enrolment, and only
        // a guarded start mints one.
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.ENROL_FACTOR)
        ) {
            return new StartEnrolmentOutcome.StepUpRequired(
                    StepUpAction.ENROL_FACTOR.wire());
        }

        // the e-mail factor's code always goes to the caller's OWN, already verified address, never
        // to a target the request names: a thief would point the codes at their own inbox
        String destination =
                FactorType.EMAIL_CODE.equals(factorType) || target == null
                        ? caller.email().value()
                        : target;

        EnrolFactor.Result result = transactionBoundary.execute(
                () -> enrolFactor.execute(caller, factorType, destination));

        return switch (result) {
            case EnrolFactor.Result.Started started ->
                    started.display() == null
                            ? new StartEnrolmentOutcome.CodeSent()
                            : new StartEnrolmentOutcome.SetupReady(started.display());
            case EnrolFactor.Result.UnsupportedFactor unsupportedFactor ->
                    new StartEnrolmentOutcome.UnsupportedFactor();
        };
    }

    public ConfirmEnrolmentOutcome confirmEnrolment(String callerEmail, String type, String proof) {

        Caller caller = callerOf(callerEmail);

        FactorType factorType;
        try {
            factorType = FactorType.of(type);
        } catch (IllegalArgumentException blank) {
            return new ConfirmEnrolmentOutcome.UnreadableFactorType();
        }

        ConfirmEnrolment.Result result = transactionBoundary.execute(
                () -> confirmEnrolment.execute(caller, factorType, proof));

        return switch (result) {
            case ConfirmEnrolment.Result.Enrolled enrolled ->
                    new ConfirmEnrolmentOutcome.Enrolled(
                            enrolled.type().value());
            case ConfirmEnrolment.Result.WrongProof wrongProof ->
                    new ConfirmEnrolmentOutcome.WrongProof();
            case ConfirmEnrolment.Result.NoPendingEnrolment noPendingEnrolment ->
                    new ConfirmEnrolmentOutcome.NoPendingEnrolment();
        };
    }

    /** {@code accessToken} is the bearer token as sent, or null when there was none. */
    public RemoveFactorOutcome removeFactor(String callerEmail, String accessToken, String type) {

        Caller caller = callerOf(callerEmail);

        // read first: the gate below spends a one-shot elevation, and a factor type that cannot
        // exist must not cost it
        FactorType factorType;
        try {
            factorType = FactorType.of(type);
        } catch (IllegalArgumentException blank) {
            return new RemoveFactorOutcome.UnreadableFactorType();
        }

        Set<Role> roles = userRepository.findBy(caller.email())
                .map(User::roles)
                .orElse(Set.of(Role.USER));

        // the floor is answered BEFORE the step-up as well as inside it: "you cannot do this at
        // all" is a better answer than "prove yourself again, and then you still cannot", and it
        // costs no elevation to say
        if (
                mfaCompliance.removalWouldBreakFloor(caller.email(), roles)
        ) {
            return new RemoveFactorOutcome.WouldBreakFloor();
        }

        // dropping a factor weakens the account, so a stolen live session must step up to do it
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.REMOVE_FACTOR)
        ) {
            return new RemoveFactorOutcome.StepUpRequired(
                    StepUpAction.REMOVE_FACTOR.wire());
        }

        // and again INSIDE the transaction that removes. The check used to happen only outside it,
        // so two removals racing each other both read "one left over the floor" and both removed;
        // here the second sees what the first wrote. (Two transactions that START together can
        // still both read the old count under READ COMMITTED: closing that needs the account's own
        // lock, as the brute-force guard takes one. The same shape of race, far less reachable.)
        return transactionBoundary.execute(() -> {
            if (
                    mfaCompliance.removalWouldBreakFloor(caller.email(), roles)
            ) {
                return new RemoveFactorOutcome.WouldBreakFloor();
            }

            enrolledFactorRepository.remove(caller.email(), factorType);

            return new RemoveFactorOutcome.Removed();
        });
    }

    /** {@code accessToken} is the bearer token as sent, or null when there was none. */
    public GenerateRecoveryCodesOutcome generateRecoveryCodes(String callerEmail, String accessToken) {

        Caller caller = callerOf(callerEmail);

        // recovery codes are spare keys: shown once, and each one signs in when a factor is out of
        // reach. A merely-live session must not be able to mint itself a set and keep them.
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.GENERATE_RECOVERY_CODES)
        ) {
            return new GenerateRecoveryCodesOutcome.StepUpRequired(
                    StepUpAction.GENERATE_RECOVERY_CODES.wire());
        }

        return new GenerateRecoveryCodesOutcome.Generated(
                transactionBoundary.execute(() -> generateRecoveryCodes.execute(caller)));
    }

    /** How many of the caller's recovery codes remain unspent. */
    public int unusedRecoveryCodes(String callerEmail) {

        Caller caller = callerOf(callerEmail);

        return enrolledFactorRepository.unusedRecoveryCodes(caller.email());
    }

    private static Caller callerOf(String callerEmail) {
        return new Caller(
                Email.of(callerEmail));
    }
}
