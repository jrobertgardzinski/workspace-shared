package com.jrobertgardzinski.clock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Clock} frozen at an instant you control: it never ticks on its own, time moves only
 * when you {@link #advance}, {@link #set} or {@link #reset} it. Backed by a single
 * {@link AtomicReference}, so one instance can serve a whole process while requests read the time
 * and others steer it concurrently — which is exactly what the time-control endpoint needs.
 *
 * <p>This is the pure {@code java.time} core, free of any framework. The same object backs the
 * application-level Cucumber glue ("14 minutes later", "the block expires") and the Micronaut
 * {@code Clock} bean, so deterministic time behaves identically through every entry point.
 */
public final class AdjustableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public AdjustableClock(Instant initial, ZoneId zone) {
        this(new AtomicReference<>(initial), zone);
    }

    private AdjustableClock(AtomicReference<Instant> instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** A clock frozen at the current real instant, read in UTC. */
    public static AdjustableClock frozenNow() {
        return new AdjustableClock(Instant.now(), ZoneOffset.UTC);
    }

    /** Move the frozen instant forward (or back, for a negative duration). */
    public void advance(Duration by) {
        instant.updateAndGet(current -> current.plus(by));
    }

    /** Freeze at a specific instant. */
    public void set(Instant to) {
        instant.set(to);
    }

    /** Re-freeze at the current real instant, discarding any manual offset. */
    public void reset() {
        instant.set(Instant.now());
    }

    @Override
    public Instant instant() {
        return instant.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new AdjustableClock(instant, zone); // shares the live instant, only the zone differs
    }
}
