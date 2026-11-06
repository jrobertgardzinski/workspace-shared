package com.jrobertgardzinski.closure;

/**
 * What a participant did with one command of the closure — one shape for the whole protocol, not
 * one per service. The three participants each had a {@code ClosureOutcome} of their own until
 * 28.09.2026, which is the mistake {@code DeletionOutcome} pointed at from the other protocol: the
 * day this vocabulary gains a case, two of the three would have been the ones that forgot it.
 *
 * <p>Over Kafka nobody reads it — a participant answers the orchestrator through
 * {@link ClosureConfirmations} and nothing else. It is read by the one-process specs, and by the
 * consumer of the participant that has no outbox of its own and builds its confirmation from
 * {@link Reserved}.
 *
 * <p>There is no failure case, for the reason the deletion protocol gives: a store that failed
 * throws, and how long to keep trying is transport, which differs per service.
 */
public sealed interface ClosureOutcome {

    /**
     * A count this axis does not report. Not zero: zero is an answer ("this person had nothing of
     * ours"), and a participant whose use case returns no number must not be made to claim one.
     */
    int UNCOUNTED = -1;

    /**
     * Marked, hidden and — where the axis confirms for itself — confirmed. Zero is a real answer,
     * and every axis counts this one, because the count is what the orchestrator is told.
     */
    record Reserved(int rows) implements ClosureOutcome {
    }

    /**
     * The closure carried out: {@code rows} destroyed, {@code leftBehind} rows of the leaver's that
     * this saga may not touch because they arrived after the mark. Either may be {@link #UNCOUNTED}.
     */
    record Erased(int rows, int leftBehind) implements ClosureOutcome {

        /** For an axis whose closure reports no numbers. */
        public static Erased uncounted() {
            return new Erased(UNCOUNTED, UNCOUNTED);
        }
    }

    /** The compensation carried out: the mark taken back off this many rows. */
    record Restored(int rows) implements ClosureOutcome {

        /** For an axis whose compensation reports no number. */
        public static Restored uncounted() {
            return new Restored(UNCOUNTED);
        }
    }

    /** The saga's topic carries every participant's commands; an unknown name is not an error. */
    record NotOurs(String type) implements ClosureOutcome {
    }

    /** Dropped WITHOUT confirming: nothing was marked and nothing was deleted. */
    record Unaddressed(String type) implements ClosureOutcome {
    }
}
