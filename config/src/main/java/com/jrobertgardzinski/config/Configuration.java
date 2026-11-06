package com.jrobertgardzinski.config;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.config.ladder.Parse;
import com.jrobertgardzinski.config.ladder.Resolution;
import com.jrobertgardzinski.config.ladder.Rung;
import com.jrobertgardzinski.config.ladder.Unparsable;
import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.config.source.restart.RestartConfigPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A deployment's configuration: the two sources a running system has, and the two ways a rule
 * can be read from them, each named after what changing the value costs. Both start from the
 * rule the code ships - the rebuild default every ladder ends in - and take the key, the type's
 * parser and the gate (the constructor) from it: {@code liveOver(MinLength.DEFAULT)}.
 *
 * <ul>
 *   <li>{@link #liveOver}: live over restart over rebuild, read on every resolution - for a rule
 *       the system asks about per use (a policy).</li>
 *   <li>{@link #boundOver}: restart over rebuild, resolved here and now - for a rule the system
 *       holds as a value for its whole life; an illegal property refuses at declaration.</li>
 * </ul>
 *
 * <p>Every {@link #liveOver} declaration also enters the {@linkplain #liveKeys() catalogue of live
 * keys}: the rules the running system may be told about, each with the gate its text must pass.
 * The declaration is the only way in, so whatever writes under a live key writes under one the
 * system reads, through the gate the system reads it with.
 */
public final class Configuration {

    private final LiveConfigPort<String> rows;
    private final RestartConfigPort<String> properties;
    /** In declaration order; a key declared again replaces its earlier entry. */
    private final Map<String, LiveKey> live = new LinkedHashMap<>();

    public Configuration(LiveConfigPort<String> rows, RestartConfigPort<String> properties) {
        this.rows = rows;
        this.properties = properties;
    }

    /**
     * A ladder over the three levels, answering with the rule holding the value in force. The key
     * joins the catalogue of live keys.
     */
    public <T, V extends ConfigValue<T>> ConfigLadder<V> liveOver(V shipped) {
        ConfigLadder<V> ladder = ladder(shipped, Rung.live(rows, parserOf(shipped)));
        live.put(shipped.key(), new DeclaredLive<>(shipped, parserOf(shipped), ladder));
        return ladder;
    }

    /** Every key declared live so far, in declaration order. */
    public Map<String, LiveKey> liveKeys() {
        return Collections.unmodifiableMap(live);
    }

    /** The live key of that name, or empty: a key nobody declared is one nobody reads. */
    public Optional<LiveKey> liveKey(String key) {
        return Optional.ofNullable(live.get(key));
    }

    /**
     * A declared live key: the same parser and the same gate the ladder reads a row with, applied
     * to text before it becomes a row.
     */
    private record DeclaredLive<T, V extends ConfigValue<T>>(V shipped, Function<String, T> parser,
                                                              ConfigLadder<V> ladder) implements LiveKey {
        @Override
        public String key() {
            return shipped.key();
        }

        @Override
        public ConfigValue<?> holding(String text) {
            T value;
            try {
                value = parser.apply(text);
            } catch (IllegalArgumentException notTheType) {
                throw new Unparsable(text, notTheType);
            }
            return shipped.holding(value);
        }

        @Override
        public Resolution<? extends ConfigValue<?>> resolution() {
            return ladder.resolution();
        }
    }

    /** The rule as the deployment binds it: its property over the shipped default, decided now. */
    public <T, V extends ConfigValue<T>> V boundOver(V shipped) {
        return ladder(shipped).resolve();
    }

    /**
     * Every ladder ends the same way - the property over the shipped default - and answers in
     * the rule's own type; what sits above is the caller's choice. The gate is the rule's
     * constructor, through {@code holding}: a candidate it refuses is reported with the value
     * it held, so the refusal stays a number in the report, not the text it was parsed from.
     */
    @SafeVarargs
    private <T, V extends ConfigValue<T>> ConfigLadder<V> ladder(V shipped, Rung<T>... above) {
        List<Rung<T>> rungs = new ArrayList<>(List.of(above));
        rungs.add(Rung.restart(properties, parserOf(shipped)));
        rungs.add(Rung.rebuild(shipped.defaultValue()));
        return ConfigLadder.of(shipped.key(), value -> shipped.holding(value), rungs)
                .map(value -> holding(shipped, value));
    }

    private static <T, V extends ConfigValue<T>> Function<String, T> parserOf(V shipped) {
        return Parse.forType(shipped.defaultValue().getClass());
    }

    /** {@code holding} answers with the rule's own type - a law in every library holds that promise. */
    @SuppressWarnings("unchecked")
    private static <T, V extends ConfigValue<T>> V holding(V shipped, T value) {
        return (V) shipped.holding(value);
    }
}
