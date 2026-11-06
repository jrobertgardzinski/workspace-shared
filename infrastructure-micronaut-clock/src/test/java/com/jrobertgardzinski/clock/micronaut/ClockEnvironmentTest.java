package com.jrobertgardzinski.clock.micronaut;

import com.jrobertgardzinski.clock.AdjustableClock;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class ClockEnvironmentTest {

    @Test
    void test_environment_provides_the_steerable_clock_and_the_controls() {
        try (ApplicationContext context = ApplicationContext.run("test")) {
            assertThat(context.getBean(Clock.class)).isInstanceOf(AdjustableClock.class);
            assertThat(context.containsBean(TimeControlController.class)).isTrue();
        }
    }

    @Test
    void production_environment_provides_the_system_clock_and_no_controls() {
        try (ApplicationContext context = ApplicationContext.builder("production")
                .deduceEnvironment(false)
                .start()) {
            assertThat(context.getBean(Clock.class)).isNotInstanceOf(AdjustableClock.class);
            assertThat(context.containsBean(TimeControlController.class)).isFalse();
        }
    }
}
