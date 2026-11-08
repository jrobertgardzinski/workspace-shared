package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Optional;

/**
 * The second half of an enrolment: one correct proof against what {@link EnrolFactor} began, and
 * the factor is persisted. Until then nothing is enrolled, so a factor cannot be added by merely
 * claiming a target.
 */
public class ConfirmEnrolment {

    private final FactorRegistry registry;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final EnrolmentChallengeStore pending;

    public ConfirmEnrolment(
                    FactorRegistry registry,
                    EnrolledFactorRepository enrolledFactorRepository,
                    EnrolmentChallengeStore pending) {
        this.registry = registry;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.pending = pending;
    }

    public sealed interface Result {

        record Enrolled(FactorType type) implements Result {}

        record WrongProof() implements Result {}

        record NoPendingEnrolment() implements Result {}
    }

    public Result execute(Caller caller, FactorType type, String proof) {

        Email user = caller.email();

        Optional<AuthenticationFactor> factor = registry.forType(type);
        Optional<EnrolmentChallengeStore.PendingEnrolment> enrolment = pending.get(user, type);

        if (
                factor.isEmpty() ||
                enrolment.isEmpty()
        ) {
            return new Result.NoPendingEnrolment();
        }

        EnrolledFactor candidate = candidate(
                user,
                type,
                enrolment.get().secretMaterial());

        if (
                !factor.get().verify(
                        candidate,
                        Optional.ofNullable(enrolment.get().challenge()),
                        proof)
        ) {
            // a wrong proof costs an attempt, and running out ends the enrolment — otherwise the
            // code can be guessed at the speed of the network for as long as the entry lives
            EnrolmentChallengeStore.PendingEnrolment afterWrongProof = enrolment.get().afterWrongProof();

            if (
                    afterWrongProof.attemptsLeft() <= 0
            ) {
                pending.remove(user, type);
            } else {
                pending.put(user, type, afterWrongProof);
            }

            return new Result.WrongProof();
        }

        // most factors store what enrolment produced; a factor whose real secret only arrives with
        // the confirming proof (WebAuthn's public key) distils it here, after verify has passed
        String stored = factor.get().enrolledMaterial(
                enrolment.get().secretMaterial(),
                proof);
        enrolledFactorRepository.enrol(
                candidate(user, type, stored));
        pending.remove(user, type);

        return new Result.Enrolled(type);
    }

    private EnrolledFactor candidate(Email user, FactorType type, String secretMaterial) {
        // New factors go to the end of the chain, in enrolment order — PAST the last position
        // taken, not at the count of what is there. Those are the same number only while nothing
        // has ever been removed: enrol three (0, 1, 2), drop the middle one, and the count is 2
        // again, so the next factor arrived at the position the third already held. Two factors
        // sharing a position leaves the chain's order down to whatever the store returns first.
        int lastPosition = enrolledFactorRepository.findByUser(user).stream()
                .mapToInt(EnrolledFactor::order)
                .max()
                .orElse(-1);
        return new EnrolledFactor(user, type, FactorLabels.of(type), lastPosition + 1, secretMaterial);
    }
}
