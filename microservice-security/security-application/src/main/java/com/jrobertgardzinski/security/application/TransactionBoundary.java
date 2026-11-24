package com.jrobertgardzinski.security.application;

import java.util.function.Supplier;

/**
 * Runs a unit of work within a transaction boundary. A use case may touch several repositories
 * (e.g. refresh rotates a session: delete + create; a brute-force trip clears failures + writes a
 * block), and those writes must commit or roll back together. Whoever calls a use case wraps the call in
 * this boundary instead of being annotated directly, so the boundary — and the transaction manager
 * it needs — exists only when a datasource is configured. With no datasource (in-memory tests) a
 * pass-through implementation is used, keeping that path free of any transaction machinery.
 */
public interface TransactionBoundary {
    <T> T execute(Supplier<T> work);
}
