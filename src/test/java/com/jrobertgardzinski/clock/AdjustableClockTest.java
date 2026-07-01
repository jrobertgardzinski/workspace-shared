package com.jrobertgardzinski.clock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AdjustableClockTest {

    private static final Instant START = Instant.parse("2026-06-15T10:00:00Z");

    @Test
    void is_frozen_and_does_not_tick_on_its_own() {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);

        Instant first = clock.instant();
        Instant second = clock.instant();

        assertThat(first).isEqualTo(START);
        assertThat(second).isEqualTo(START);
    }

    @Test
    void advance_moves_the_instant_forward() {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);

        clock.advance(Duration.ofMinutes(15));

        assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofMinutes(15)));
    }

    @Test
    void advance_accumulates() {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);

        clock.advance(Duration.ofMinutes(14));
        clock.advance(Duration.ofMinutes(1));

        assertThat(clock.instant()).isEqualTo(START.plus(Duration.ofMinutes(15)));
    }

    @Test
    void advance_with_a_negative_duration_moves_back() {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);

        clock.advance(Duration.ofMinutes(-5));

        assertThat(clock.instant()).isEqualTo(START.minus(Duration.ofMinutes(5)));
    }

    @Test
    void set_freezes_at_the_given_instant() {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);
        Instant target = Instant.parse("2030-01-01T00:00:00Z");

        clock.set(target);

        assertThat(clock.instant()).isEqualTo(target);
    }

    @Test
    void reset_returns_to_real_time() {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);
        Instant before = Instant.now();

        clock.reset();

        Instant after = Instant.now();
        assertThat(clock.instant()).isBetween(before, after);
    }

    @Test
    void with_zone_shares_the_live_instant() {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);

        var viewInOtherZone = clock.withZone(ZoneOffset.ofHours(2));
        clock.advance(Duration.ofHours(1));

        assertThat(viewInOtherZone.getZone()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(viewInOtherZone.instant()).isEqualTo(START.plus(Duration.ofHours(1)));
    }

    @Test
    void advance_is_thread_safe() throws InterruptedException {
        AdjustableClock clock = new AdjustableClock(START, ZoneOffset.UTC);
        int threads = 16;
        int advancesPerThread = 1_000;
        var pending = new AtomicInteger(threads);

        var workers = IntStream.range(0, threads)
                .mapToObj(i -> new Thread(() -> {
                    pending.decrementAndGet();
                    for (int n = 0; n < advancesPerThread; n++) {
                        clock.advance(Duration.ofSeconds(1));
                    }
                }))
                .toList();
        workers.forEach(Thread::start);
        for (Thread worker : workers) {
            worker.join();
        }

        long expectedSeconds = (long) threads * advancesPerThread;
        assertThat(clock.instant()).isEqualTo(START.plusSeconds(expectedSeconds));
    }
}
