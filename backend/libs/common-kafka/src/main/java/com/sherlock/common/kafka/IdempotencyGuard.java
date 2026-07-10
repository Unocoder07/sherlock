package com.sherlock.common.kafka;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bounded in-memory dedupe on envelope {@code event_id}. Delivery is at-least-once
 * (doc 03 §4), so every consumer must be idempotent; this guard drops re-deliveries
 * of an event already applied.
 *
 * <p>An LRU-bounded set is sufficient for a single stateless consumer instance
 * (Evidence Fusion). Stateful services that must dedupe across restarts/rebalances
 * (Confidence Engine) additionally rely on their durable last-applied-offset;
 * a Redis-backed guard can replace this behind the same {@link #seen(String)} call.
 */
public final class IdempotencyGuard {

    private final Set<String> recent;

    public IdempotencyGuard(int capacity) {
        this.recent = Collections.newSetFromMap(new LinkedHashMap<>(capacity * 2, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > capacity;
            }
        });
    }

    /**
     * Record {@code eventId} and report whether it was already seen.
     *
     * @return {@code true} if this event was seen before (caller should skip it),
     *         {@code false} if it is new (caller should process it).
     */
    public synchronized boolean seen(String eventId) {
        // add() returns false if the element was already present.
        return !recent.add(eventId);
    }
}
