package com.jrobertgardzinski.observation;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two behaviours this library exists for. Both are about the same rule: watching is not the
 * work, so nothing here may decide whether the work happens.
 */
@Epic("Observability")
@Feature("Stating facts")
class ObservationsTest {

    /** A stand-in for a service's own sealed catalogue — the library never sees a real one. */
    private record SomethingHappened(String what) {
    }

    @Test
    @DisplayName("silence accepts every fact and does nothing with it")
    void silence_is_not_a_failure() {
        Observations<SomethingHappened> nobody = Observations.silent();

        assertDoesNotThrow(() -> nobody.record(new SomethingHappened("a saga compensated")));
    }

    @Test
    @DisplayName("one fact reaches every watcher, in the order they were given")
    void the_fact_reaches_everyone() {
        List<String> told = new ArrayList<>();
        Observations<SomethingHappened> both = Observations.fanningOutTo(List.of(
                fact -> told.add("A:" + fact.what()),
                fact -> told.add("B:" + fact.what())));

        both.record(new SomethingHappened("backlog"));

        assertEquals(List.of("A:backlog", "B:backlog"), told,
                "a migration runs the new tool beside the old one, and both must see the SAME"
                        + " statement — that is what makes them comparable");
    }

    @Test
    @DisplayName("a watcher that throws does not take the others, or the work, down with it")
    void a_broken_watcher_is_contained() {
        List<String> told = new ArrayList<>();
        Observations<SomethingHappened> both = Observations.fanningOutTo(List.of(
                fact -> {
                    throw new IllegalStateException("exporter is on fire");
                },
                fact -> told.add("B:" + fact.what())));

        assertDoesNotThrow(() -> both.record(new SomethingHappened("backlog")),
                "a service that fell over because nobody could write down what it noticed has been"
                        + " broken by its own instrumentation");
        assertEquals(List.of("B:backlog"), told,
                "and the watcher that still works must still be told");
    }

    @Test
    @DisplayName("who is watching cannot change after the fan-out is built")
    void the_watchers_are_fixed() {
        List<String> told = new ArrayList<>();
        List<Observations<SomethingHappened>> watchers =
                new ArrayList<>(List.of(fact -> told.add("A")));
        Observations<SomethingHappened> both = Observations.fanningOutTo(watchers);

        watchers.add(fact -> told.add("B"));   // too late
        both.record(new SomethingHappened("backlog"));

        assertEquals(List.of("A"), told);
    }

    @Test
    @DisplayName("fanning out to nobody is silence, not an error")
    void an_empty_fan_out_is_silence() {
        Observations<SomethingHappened> nobody = Observations.fanningOutTo(List.of());

        assertDoesNotThrow(() -> nobody.record(new SomethingHappened("backlog")));
    }
}
