package com.jrobertgardzinski.closure;

import com.jrobertgardzinski.identity.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The three commands, the guard in front of them and the one switch between them — the part of
 * being a participant that is the same on every axis, in one place instead of three.
 *
 * <p>Until 28.09.2026 each participant carried its own copy of everything below: the "is this even
 * ours" check, the drop of a command that names nobody, the switch, and the warning about a mark
 * that reserved nothing. The copies had already begun to drift in the only way that is invisible —
 * the wording of a log line — and the day the protocol gains a fourth command, two of the three
 * would have been the ones that did not learn it. {@code ClosureParticipantContractTest} held all
 * three to the same promises; this class is the same agreement on the production side.
 *
 * <p>What stays with the subclass is everything that is ABOUT the axis: which use case a step runs,
 * what it counts, what it announces, and the sentences it logs in its own voice. The logger here is
 * {@code getClass()}'s on purpose — a line this class emits is filed under the participant that
 * emitted it, which is what the services' log tests read and what an operator greps for.
 *
 * <p>Knows nothing about Kafka, so a subclass of it runs unchanged in one process.
 */
public abstract class ClosureParticipant {

    public static final String MARK = ClosureMessages.PURGE_USER_CONTENT;
    public static final String ERASE = ClosureMessages.ERASE_USER_CONTENT;
    public static final String RESTORE = ClosureMessages.RESTORE_USER_CONTENT;

    /** The subclass's own logger: the participant, not this skeleton, is what a log line names. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    public final ClosureOutcome handle(ClosureCommand command) {
        String type = command.type();
        if (!MARK.equals(type) && !ERASE.equals(type) && !RESTORE.equals(type)) {
            return new ClosureOutcome.NotOurs(type);
        }
        String sagaId = command.sagaId();
        if (!command.isAddressed()) {
            // confirming would advance the saga on a deletion that never happened
            log.warn("dropping {} without a user id (saga {})", type, sagaId);
            return new ClosureOutcome.Unaddressed(type);
        }
        UserId leaver = command.userId();
        return switch (type) {
            case MARK -> {
                int reserved = reserve(sagaId, leaver);
                marked(sagaId, reserved);   // after the mark is committed, never before
                if (reserved == 0) {
                    // "nothing of theirs" and "rows still under their old address" look the same
                    // from here, so the saga is told zero AND somebody is told to come and look
                    reservedNothing();
                    log.warn("the mark reserved NOTHING, and that is what the orchestrator is "
                            + "told (saga {})", sagaId);
                }
                yield new ClosureOutcome.Reserved(reserved);
            }
            case ERASE -> erase(command, leaver);
            case RESTORE -> restore(sagaId, leaver);
            default -> throw new IllegalStateException("unreachable: " + type);
        };
    }

    /**
     * Hide this leaver's rows, reversibly, and answer how many were reserved — and, on the axes
     * that confirm for themselves, confirm that count in the same breath (see
     * {@link AtomicClosureParticipant}).
     */
    protected abstract int reserve(String sagaId, UserId leaver);

    /**
     * Told what the mark reserved, once it is committed, so the axis can say it in its own words.
     * Not part of the protocol — a participant that logs nothing here is a participant that logs
     * nothing here.
     */
    protected void marked(String sagaId, int rows) {
    }

    /**
     * Destroy what the mark reserved. The whole command is handed over, not just the leaver: the
     * conditions beside the closure are read HERE, by the axis that knows what they mean.
     */
    protected abstract ClosureOutcome erase(ClosureCommand command, UserId leaver);

    /** Take the mark back: the saga gave up and this axis is not the one that decides. */
    protected abstract ClosureOutcome restore(String sagaId, UserId leaver);

    /**
     * Told when a mark reserved nothing, so the axis can record it as its own observation. The log
     * line is this class's; the metric is the service's own word, on its own domain's vocabulary.
     */
    protected abstract void reservedNothing();
}
