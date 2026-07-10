package com.sherlock.timeline.domain;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Decides which enriched verdicts deserve a timeline entry (doc 02 §6). Pure
 * decision logic — no proto, no persistence — so it is unit-testable in isolation.
 *
 * <p>Two entry kinds are produced:
 * <ul>
 *   <li>{@code STATE_TRANSITION} whenever the state label changes; this also
 *       re-baselines the inflection detector to the transition's score.</li>
 *   <li>{@code SCORE_INFLECTION} when the score moves at least
 *       {@code inflectionThreshold} from the last recorded baseline without a
 *       state change — so the timeline captures notable swings, not every tick.</li>
 * </ul>
 * The per-participant baseline is best-effort in-memory state; the durable rows
 * remain the source of truth (a restart replays the topic from the earliest
 * offset and rebuilds it).
 */
public class TimelineProjector {

    /** What kind of entry a verdict earned (mirrors the proto {@code EntryKind}). */
    public enum Kind {
        STATE_TRANSITION,
        SCORE_INFLECTION
    }

    /** A decided entry, sans identity/persistence concerns. */
    public record Projected(Kind kind, String fromState, String toState, double score) {
    }

    private final double inflectionThreshold;

    /** meetingId:participantId -> last recorded score (baseline for inflection). */
    private final ConcurrentMap<String, Double> baseline = new ConcurrentHashMap<>();

    public TimelineProjector(double inflectionThreshold) {
        this.inflectionThreshold = inflectionThreshold;
    }

    /**
     * Decide whether this verdict earns a timeline entry.
     *
     * @param state     current clean state label
     * @param prevState previous clean state label (as reported by the verdict)
     * @param score     current score 0..1
     */
    public Optional<Projected> project(String meetingId, String participantId,
                                       String state, String prevState, double score) {
        String key = meetingId + ":" + participantId;
        boolean firstSight = !baseline.containsKey(key);
        double base = baseline.getOrDefault(key, score);

        if (isTransition(state, prevState)) {
            baseline.put(key, score);
            return Optional.of(new Projected(Kind.STATE_TRANSITION, prevState, state, score));
        }

        if (!firstSight && Math.abs(score - base) >= inflectionThreshold) {
            baseline.put(key, score);
            return Optional.of(new Projected(Kind.SCORE_INFLECTION, state, state, score));
        }

        if (firstSight) {
            baseline.put(key, score);
        }
        return Optional.empty();
    }

    private static boolean isTransition(String state, String prevState) {
        return prevState != null && !prevState.isBlank() && !prevState.equals(state);
    }
}
