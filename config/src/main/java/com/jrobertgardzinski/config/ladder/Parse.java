package com.jrobertgardzinski.config.ladder;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;

/**
 * Parsers for the text a property source or a settings table holds. Each refuses text that is not
 * its type with an {@link IllegalArgumentException}, which a rung turns into a refusal of that
 * level: at declaration for a level bound before serving, per resolution for the live one.
 */
public final class Parse {

    private Parse() {
    }

    public static Integer integer(String text) {
        return Integer.valueOf(text.trim());
    }

    /** Strict: {@code true} or {@code false}, any case, and nothing else. */
    public static Boolean bool(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("must be true or false, not '" + text + "'");
        };
    }

    public static String text(String text) {
        return text;
    }

    /**
     * The parser for a value's type: integers, flags, text, and any enum by its constant name
     * (trimmed, any case). A type with no parser is refused where the ladder is declared.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Function<String, T> forType(Class<?> type) {
        if (type == Integer.class) return text -> (T) integer(text);
        if (type == Boolean.class) return text -> (T) bool(text);
        if (type == String.class) return text -> (T) text(text);
        if (type.isEnum()) return text -> (T) constant((Class<? extends Enum>) type, text);
        throw new IllegalArgumentException("no parser for configuration values of type " + type.getName());
    }

    private static <E extends Enum<E>> E constant(Class<E> type, String text) {
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("must be one of " + Arrays.toString(type.getEnumConstants()) + ", not '" + text + "'");
        }
    }
}
