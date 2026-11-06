package com.jrobertgardzinski.config.ladder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The one implementation of {@link ConfigLadder}: rungs in descending order, one per level, the
 * rebuild default at the bottom. Rungs bound before serving are read and gated once, here; a live
 * rung is read on every resolution. A refusal of the live rung is logged once per distinct
 * refusal, not once per resolution: the same illegal row asked a thousand times is one line.
 */
final class RungLadder<T> implements ConfigLadder<T> {

    private static final System.Logger LOG = System.getLogger(RungLadder.class.getName());

    private final String key;
    private final Consumer<T> gate;
    private final List<Rung<T>> rungs;
    /** What the rungs bound before serving held when the ladder was declared, already gated. */
    private final Map<Level, Optional<T>> boundBeforeServing = new EnumMap<>(Level.class);
    /** The last refusal logged for the live rung, so the log does not repeat itself. */
    private volatile Resolution.Rejected lastLogged;

    RungLadder(String key, Consumer<T> gate, List<Rung<T>> rungs) {
        this.key = Objects.requireNonNull(key, "key");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.rungs = List.copyOf(rungs);
        requireWellFormed();
        for (Rung<T> rung : this.rungs) {
            if (rung.level().boundBeforeServing()) {
                boundBeforeServing.put(rung.level(), gatedNow(rung));
            }
        }
    }

    private void requireWellFormed() {
        if (rungs.isEmpty() || rungs.getLast().level() != Level.REBUILD) {
            throw new IllegalArgumentException(
                    "ladder for '" + key + "' must end in a rebuild default - there is no ladder without one");
        }
        for (int i = 1; i < rungs.size(); i++) {
            Level above = rungs.get(i - 1).level();
            Level below = rungs.get(i).level();
            if (above.compareTo(below) >= 0) {
                throw new IllegalArgumentException(
                        "ladder for '" + key + "' is out of order: " + above.label() + " may not sit above "
                                + below.label() + " - rungs go from the level bound latest to the default");
            }
        }
    }

    /** A rung bound before serving must be legal now, or there is no ladder at all. */
    private Optional<T> gatedNow(Rung<T> rung) {
        Candidate<T> candidate = candidate(rung);
        if (candidate.refusal() != null) {
            throw new IllegalArgumentException(
                    "illegal value for '" + key + "' at the " + rung.level().label() + " level ("
                            + candidate.refusal().value() + "): " + candidate.refusal().reason());
        }
        if (rung.level() == Level.REBUILD && candidate.value().isEmpty()) {
            throw new IllegalArgumentException("ladder for '" + key + "' has an empty rebuild default");
        }
        return candidate.value();
    }

    /** What a rung holds under the key, read and gated: a value, nothing, or a refusal. */
    private record Candidate<T>(Optional<T> value, Resolution.Rejected refusal) {
    }

    private Candidate<T> candidate(Rung<T> rung) {
        Optional<T> read;
        try {
            read = rung.read().apply(key);
        } catch (Unparsable notTheType) {
            return new Candidate<>(Optional.empty(),
                    new Resolution.Rejected(rung.level().label(), notTheType.raw(), notTheType.getMessage()));
        }
        if (read.isPresent()) {
            try {
                gate.accept(read.get());
            } catch (IllegalArgumentException illegal) {
                return new Candidate<>(Optional.empty(),
                        new Resolution.Rejected(rung.level().label(), read.get(), illegal.getMessage()));
            }
        }
        return new Candidate<>(read, null);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Resolution<T> resolution() {
        List<Resolution.Rejected> rejected = new ArrayList<>();
        for (Rung<T> rung : rungs) {
            if (rung.level().boundBeforeServing()) {
                Optional<T> bound = boundBeforeServing.get(rung.level());
                if (bound.isPresent()) {
                    return new Resolution<>(bound.get(), rung.level().label(), rejected);
                }
                continue;
            }
            Candidate<T> candidate = candidate(rung);
            if (candidate.refusal() != null) {
                logOnce(candidate.refusal());
                rejected.add(candidate.refusal());
            } else if (candidate.value().isPresent()) {
                return new Resolution<>(candidate.value().get(), rung.level().label(), rejected);
            }
        }
        throw new IllegalStateException("ladder for '" + key + "' ran out of rungs - the rebuild default is gated at declaration");
    }

    private void logOnce(Resolution.Rejected refusal) {
        if (refusal.equals(lastLogged)) {
            return;
        }
        lastLogged = refusal;
        LOG.log(System.Logger.Level.WARNING,
                "illegal value for ''{0}'' at the {1} level ({2}) - falling through: {3}",
                key, refusal.source(), refusal.value(), refusal.reason());
    }
}
