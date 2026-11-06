package com.jrobertgardzinski.closure;

import com.jrobertgardzinski.identity.UserId;

import java.util.Optional;

/**
 * A command of the closure, as a participant needs to read it: the agreement's own fields
 * ({@link ClosureMessages.Field}), already off the wire.
 *
 * <p>This is still vocabulary and not transport — nothing here says how the command arrived, and
 * that is the point. Three participants were each about to declare their own record of exactly
 * these fields, which is the shape of drift this library exists to prevent: the day the
 * agreement gains a field, one of the three would have been the one that forgot it.
 *
 * <p>{@code rule} is the condition stated for THE READER'S OWN axis — the reader pulls it out of
 * {@link ClosureMessages.Field#POLICY} under its own name, because only it knows what that name
 * is, and only it knows what the words mean. Empty means the command stated nothing, which is not
 * the same as stating "destroy": absent leaves the deployment its say, and a self-closure does
 * not (see {@link ClosureInitiator}).
 *
 * @param type        the command's name on the wire
 * @param sagaId      the orchestrator's handle on this closure; the only one of these safe to log
 * @param userId      whose account is closing, by identity; the address is not on the command
 * @param initiatedBy {@link ClosureInitiator} as a word; decides whether conditions count at all
 * @param rule        the condition stated for the reader's own axis, if the command carried one
 */
public record ClosureCommand(String type, String sagaId, UserId userId, String initiatedBy,
                             Optional<String> rule) {

    /** Whether this command names anybody at all. A command keyed by nobody must not be acted on. */
    public boolean isAddressed() {
        return userId != null;
    }

    /** The wire form of {@link ClosureMessages.Field#USER_ID} back into the type; blank or mangled reads as absent. */
    public static Optional<UserId> userIdOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UserId.of(raw));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    /** Whether the conditions beside this closure may be honoured — the initiator decides. */
    public boolean allowsConditions() {
        return ClosureInitiator.allowsConditions(initiatedBy);
    }
}
