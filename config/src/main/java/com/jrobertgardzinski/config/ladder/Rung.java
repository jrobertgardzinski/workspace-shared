package com.jrobertgardzinski.config.ladder;

import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.config.source.restart.RestartConfigPort;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One rung of a ladder: a level and the way to read that level under a key. An empty answer is a
 * vacant rung the ladder falls through. A rung over a TEXT source takes a parser, since a property
 * file and a settings table both hold text and the type enters here: text that is not the type is
 * a refused candidate of that level, exactly like a value the gate refuses.
 */
public record Rung<T>(Level level, Function<String, Optional<T>> read) {

    public Rung {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(read, "read");
    }

    public static <T> Rung<T> live(LiveConfigPort<T> port) {
        Objects.requireNonNull(port, "live port");
        return new Rung<>(Level.LIVE, key -> Optional.ofNullable(port.find(key)));
    }

    public static <T> Rung<T> live(LiveConfigPort<String> port, Function<String, T> parser) {
        Objects.requireNonNull(port, "live port");
        Objects.requireNonNull(parser, "parser");
        return new Rung<>(Level.LIVE, key -> parsed(port.find(key), parser));
    }

    public static <T> Rung<T> restart(RestartConfigPort<T> port) {
        Objects.requireNonNull(port, "restart port");
        return new Rung<>(Level.RESTART, key -> Optional.ofNullable(port.find(key)));
    }

    public static <T> Rung<T> restart(RestartConfigPort<String> port, Function<String, T> parser) {
        Objects.requireNonNull(port, "restart port");
        Objects.requireNonNull(parser, "parser");
        return new Rung<>(Level.RESTART, key -> parsed(port.find(key), parser));
    }

    public static <T> Rung<T> rebuild(T defaultValue) {
        Objects.requireNonNull(defaultValue, "rebuild default");
        return new Rung<>(Level.REBUILD, key -> Optional.of(defaultValue));
    }

    private static <T> Optional<T> parsed(String raw, Function<String, T> parser) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.apply(raw));
        } catch (IllegalArgumentException notTheType) {
            throw new Unparsable(raw, notTheType);
        }
    }
}
