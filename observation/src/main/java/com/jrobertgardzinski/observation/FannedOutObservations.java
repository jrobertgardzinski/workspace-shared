package com.jrobertgardzinski.observation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The implementation behind {@link Observations#fanningOutTo}. Package-private: callers ask for the
 * behaviour, never for the class, so the day this grows a queue or a per-watcher timeout nobody
 * has to be told.
 */
final class FannedOutObservations<F> implements Observations<F> {

    private static final Logger LOG = LoggerFactory.getLogger(FannedOutObservations.class);

    private final List<Observations<F>> watchers;

    FannedOutObservations(List<Observations<F>> watchers) {
        this.watchers = List.copyOf(watchers);
    }

    @Override
    public void record(F fact) {
        for (Observations<F> watcher : watchers) {
            try {
                watcher.record(fact);
            } catch (RuntimeException broken) {
                // the fact still reaches everyone else, and the failure is reported where a
                // failure can be read — through the log, not through the siblings, and never by
                // re-throwing into the work that merely wanted to say what it had noticed
                LOG.warn("an observer ({}) threw while being told about a fact; the others were"
                                + " still told, and the work goes on",
                        watcher.getClass().getName(), broken);
            }
        }
    }
}
