package com.jrobertgardzinski.clock.micronaut;

import com.jrobertgardzinski.clock.AdjustableClock;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the controls over real HTTP against an embedded server (which Micronaut starts in the
 * {@code test} environment), proving the endpoint moves the very {@link Clock} the rest of the
 * service would inject.
 */
class TimeControlEndpointTest {

    private EmbeddedServer server;
    private BlockingHttpClient client;

    @BeforeEach
    void startServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server.getApplicationContext()
                .createBean(HttpClient.class, server.getURL())
                .toBlocking();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void advance_moves_time_forward() {
        Instant before = now();

        Instant after = instantOf(client.retrieve(
                HttpRequest.POST("/test/clock/advance", Map.of("duration", "PT15M")), Map.class));

        assertThat(after).isEqualTo(before.plus(Duration.ofMinutes(15)));
    }

    @Test
    void set_freezes_at_a_given_instant() {
        Instant target = Instant.parse("2030-01-01T00:00:00Z");

        Instant after = instantOf(client.retrieve(
                HttpRequest.POST("/test/clock/set", Map.of("instant", target.toString())), Map.class));

        assertThat(after).isEqualTo(target);
    }

    @Test
    void reset_returns_to_real_time() {
        client.exchange(HttpRequest.POST("/test/clock/set", Map.of("instant", "2000-01-01T00:00:00Z")));
        Instant lowerBound = Instant.now();

        Instant after = instantOf(client.exchange(HttpRequest.POST("/test/clock/reset", null), Map.class).body());

        assertThat(after).isAfterOrEqualTo(lowerBound);
    }

    @Test
    void the_endpoint_steers_the_clock_the_service_injects() {
        Clock injected = server.getApplicationContext().getBean(Clock.class);
        assertThat(injected).isInstanceOf(AdjustableClock.class);

        client.exchange(HttpRequest.POST("/test/clock/set", Map.of("instant", "2027-03-01T12:00:00Z")));

        assertThat(injected.instant()).isEqualTo(Instant.parse("2027-03-01T12:00:00Z"));
    }

    private Instant now() {
        return instantOf(client.retrieve(HttpRequest.GET("/test/clock"), Map.class));
    }

    @SuppressWarnings("unchecked")
    private Instant instantOf(Object body) {
        return Instant.parse((String) ((Map<String, String>) body).get("instant"));
    }
}
