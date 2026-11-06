package com.jrobertgardzinski.clock.micronaut;

import com.jrobertgardzinski.clock.AdjustableClock;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Test-only HTTP controls for the steerable clock. Present only when the {@code test} environment
 * is active, so a deployed test/staging instance — and its Angular UI — can move time, while a
 * production build never exposes it. It drives the same {@link AdjustableClock} that backs every
 * time-aware component, so advancing here advances the whole service. Times are ISO-8601:
 * {@code Duration} (e.g. {@code PT15M}) for advancing, {@code Instant} for setting.
 */
@Controller("/test/clock")
@Requires(env = "test")
public class TimeControlController {

    private final AdjustableClock clock;

    public TimeControlController(AdjustableClock clock) {
        this.clock = clock;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    public Map<String, String> now() {
        return Map.of("instant", clock.instant().toString());
    }

    @Post(value = "/advance", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public Map<String, String> advance(@Body Map<String, String> body) {
        clock.advance(Duration.parse(body.get("duration")));
        return now();
    }

    @Post(value = "/set", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public Map<String, String> set(@Body Map<String, String> body) {
        clock.set(Instant.parse(body.get("instant")));
        return now();
    }

    @Post(value = "/reset", produces = MediaType.APPLICATION_JSON)
    public Map<String, String> reset() {
        clock.reset();
        return now();
    }
}
