package com.jrobertgardzinski.config.ladder;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One key, resolved over a ladder of rungs: the level bound latest in the lifecycle wins. A
 * ladder declares its key, and every candidate passes the same gate, typically a value-object
 * constructor. Which rungs a key has is the key's own business: live over restart over rebuild,
 * live over rebuild, restart over rebuild, or rebuild alone. Every ladder ends in rebuild, so
 * there is always an answer.
 *
 * <p>WHEN a candidate is gated depends on where it is bound. The default and the property are
 * fixed before the process serves, so they are gated when the ladder is declared: an illegal one
 * refuses to build the ladder and the deployment fails at startup, naming the key, the level, the
 * value and the reason. Only a live rung is read per {@link #resolution()}, and only it can be
 * refused: a hand-edited row is logged, skipped, and the ladder falls through.
 */
public interface ConfigLadder<T> {

    String key();

    /** The answer together with its provenance: which level answered and what was refused on the way. */
    Resolution<T> resolution();

    default T resolve() {
        return resolution().value();
    }

    /** Rungs from the highest to the lowest; the last one must be the rebuild default. */
    @SafeVarargs
    static <T> ConfigLadder<T> of(String key, Consumer<T> gate, Rung<T>... rungs) {
        return new RungLadder<>(key, gate, List.of(rungs));
    }

    static <T> ConfigLadder<T> of(String key, Consumer<T> gate, List<Rung<T>> rungs) {
        return new RungLadder<>(key, gate, rungs);
    }

    /** The same ladder answering in another type: the value mapped, the provenance untouched. */
    default <U> ConfigLadder<U> map(Function<T, U> f) {
        ConfigLadder<T> self = this;
        return new ConfigLadder<>() {
            @Override
            public String key() {
                return self.key();
            }

            @Override
            public Resolution<U> resolution() {
                Resolution<T> resolved = self.resolution();
                return new Resolution<>(f.apply(resolved.value()), resolved.source(), resolved.rejected());
            }
        };
    }
}
