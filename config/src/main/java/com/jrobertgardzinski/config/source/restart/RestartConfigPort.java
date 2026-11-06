package com.jrobertgardzinski.config.source.restart;

/**
 * SPI over application properties, environment variables or any other start-time property source.
 * The name promises the SEMANTICS — a value bound at process start, changed by a restart — and
 * the adapter names the technology. Returns {@code null} when the property is not set: absence
 * is a vacant level for a layered resolver to fall through, not an error.
 */
public interface RestartConfigPort<T> {
    T find(String name);
}
