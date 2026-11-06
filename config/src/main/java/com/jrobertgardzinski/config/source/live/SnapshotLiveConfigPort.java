package com.jrobertgardzinski.config.source.live;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The live level as one snapshot of the whole settings table: every key is answered from the same
 * read, so a policy of five keys costs one round trip, not five, and no question ever reaches the
 * table. The table is read exactly twice in the life of the service: once here, when the service
 * starts, and once after each of its own writes, when the writer calls {@link #refresh()} and sees
 * its decision at once.
 *
 * <p>The table belongs to the service and the service's API is the only way in, so there is nothing
 * to poll for: a row written behind the API's back is not the service's decision and is not
 * noticed until the next start or the next write. A table that cannot be read when the service
 * starts fails the start, like an illegal property does; one that cannot be read after a write
 * fails the write, and the snapshot in force stays as it was.
 */
public final class SnapshotLiveConfigPort implements LiveConfigPort<String> {

    private final Supplier<Map<String, String>> table;
    private volatile Map<String, String> rows;

    /** {@code table} is one read of the whole table: every row, key to text. Read once, here. */
    public SnapshotLiveConfigPort(Supplier<Map<String, String>> table) {
        this.table = table;
        refresh();
    }

    @Override
    public String find(String name) {
        return rows.get(name);
    }

    /** Take a fresh snapshot now — what a writer calls after its own write. */
    public void refresh() {
        rows = Collections.unmodifiableMap(new HashMap<>(table.get()));
    }
}
