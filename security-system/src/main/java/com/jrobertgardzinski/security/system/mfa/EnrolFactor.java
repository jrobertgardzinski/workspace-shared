package com.jrobertgardzinski.security.system.mfa;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.vo.FactorType;

import java.util.Optional;

/**
 * Begins the enrolment of a new factor for a signed-in user: a code factor sends a code to the
 * target; a possession factor generates a secret and returns what to show — a TOTP
 * {@code otpauth://} URI. Nothing is enrolled yet. A factor cannot be added by merely claiming a
 * target: {@link ConfirmEnrolment} accepts one correct proof and only then persists it, the same
 * verification that guards sign-in.
 */
public class EnrolFactor {

    private final FactorRegistry registry;
    private final EnrolmentChallengeStore pending;

    public EnrolFactor(
                    FactorRegistry registry,
                    EnrolmentChallengeStore pending) {
        this.registry = registry;
        this.pending = pending;
    }

    public sealed interface Result {

        /** Enrolment begun; {@code display} is shown to the user (a TOTP otpauth URI) or empty (a code was sent). */
        record Started(String display) implements Result {}

        record UnsupportedFactor() implements Result {}
    }

    public Result execute(Email user, FactorType type, String target) {

        Optional<AuthenticationFactor> factor = registry.forType(type);

        if (
                factor.isEmpty()
        ) {
            return new Result.UnsupportedFactor();
        }

        EnrolmentSetup setup = factor.get().beginEnrolment(target);
        pending.put(
                user,
                type,
                EnrolmentChallengeStore.PendingEnrolment.beginning(
                        setup.secretMaterial(),
                        setup.challenge()));

        return new Result.Started(
                setup.display());
    }
}
