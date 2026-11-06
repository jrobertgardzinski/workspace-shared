package com.jrobertgardzinski.clock.micronaut;

import com.jrobertgardzinski.clock.AdjustableClock;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * Supplies the {@link Clock} every time-aware component injects. Which clock depends on the
 * environment:
 * <ul>
 *   <li>normally &rarr; the real, ticking {@link Clock#systemUTC() system clock};</li>
 *   <li>under the {@code test} environment &rarr; a frozen {@link AdjustableClock} that the
 *       {@link TimeControlController} can steer.</li>
 * </ul>
 * The test clock is exposed under both {@code AdjustableClock} (so the controller can drive it)
 * and {@code Clock} (so everything else injects it as the one source of time) — a single instance,
 * two views. Only one of the two beans exists in any given environment, so {@code Clock} resolves
 * unambiguously.
 */
@Factory
public class ClockFactory {

    @Singleton
    @Requires(notEnv = "test")
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Singleton
    @Requires(env = "test")
    @Bean(typed = {AdjustableClock.class, Clock.class})
    AdjustableClock adjustableClock() {
        return AdjustableClock.frozenNow();
    }
}
