package com.jrobertgardzinski.observation;

import java.util.List;

/**
 * Where a service's own facts go — the one port between "the code has noticed something" and
 * whatever is watching this month.
 *
 * <p>Deliberately the narrowest interface that can carry any fact: a caller states WHAT happened
 * and nothing about how it should be counted, named, labelled or alerted on. Those are the watching
 * tool's decisions, and they are exactly what changes when the tool does. The type parameter is the
 * service's own sealed catalogue of facts — this library never sees one, and must not: a stuck
 * erasure backlog and a compensated deletion are sentences about somebody's business, not about
 * observability.
 *
 * <p><strong>An implementation must not throw.</strong> Watching is not the work, and a service
 * that fell over because nobody could write down what it noticed has been broken by its own
 * instrumentation. {@link #fanningOutTo} enforces that for a group; a single adapter is on its
 * honour.
 *
 * @param <F> the service's fact type
 */
@FunctionalInterface
public interface Observations<F> {

    void record(F fact);

    /**
     * What a service does when nothing is watching: it goes on working.
     *
     * <p>This is a boundary made real rather than described. A service with no watcher is not a
     * broken service, so the port has an answer even with no adapter assembled — which is what lets
     * a composition root leave observability out entirely and a test drive the real work with
     * nobody listening.
     *
     * <p>It states nothing anywhere on purpose, not even a log line: a "nobody is listening"
     * warning once a minute is itself a watcher, and a noisy one.
     */
    static <F> Observations<F> silent() {
        return fact -> { };
    }

    /**
     * One fact, several watchers — the shape a migration takes: the new tool runs beside the old
     * one, both are fed from the same statement, and the two can be compared before anything is
     * switched off. Because the fact is stated ONCE, the watchers cannot disagree about what
     * happened; only about what to call it.
     *
     * <p>Each watcher is called inside its own try/catch, and that is the whole point rather than
     * defensive habit: without it, the first exporter to break takes down every other one and the
     * work that was being observed with them. A watcher that throws is reported through the log —
     * never through its siblings, which have their own opinions about what a fact is — and the
     * others still get the fact.
     *
     * <p>The list is copied, so a caller cannot change who is watching after the fact.
     */
    static <F> Observations<F> fanningOutTo(List<Observations<F>> watchers) {
        return new FannedOutObservations<>(watchers);
    }
}
