package com.sherlock.confidence.domain;

import com.sherlock.contracts.confidence.v1.ParticipantState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All per-participant beliefs for one meeting. Owns the cross-participant concern
 * — {@code separation} = score(top) − score(second) — which the IDENTIFIED guard
 * needs so we never crown a weak leader in an ambiguous meeting (doc 05 §4, §6).
 *
 * <p>A {@code tick} recomputes every participant's score at an instant, derives
 * separation, reclassifies each, and returns the verdicts whose state changed —
 * exactly what the outbound adapter publishes to {@code confidence.updates}.
 */
public final class MeetingBelief {

    /** A per-participant verdict produced by a tick (state transition or first classification). */
    public record VerdictUpdate(
            String participantId,
            ScoreResult result,
            ParticipantState previousState,
            ParticipantState newState,
            double separation) {
    }

    private final String meetingId;
    private final Map<String, ParticipantBelief> participants = new LinkedHashMap<>();

    public MeetingBelief(String meetingId) {
        this.meetingId = meetingId;
    }

    public String meetingId() {
        return meetingId;
    }

    public void apply(Evidence e) {
        participants.computeIfAbsent(e.participantId(), ParticipantBelief::new).apply(e);
    }

    public void markLeft(String participantId) {
        ParticipantBelief b = participants.get(participantId);
        if (b != null) {
            b.markLeft();
        }
    }

    /**
     * Recompute all beliefs at {@code nowMs}; return the verdicts whose state
     * changed (so the caller publishes only real transitions).
     */
    public List<VerdictUpdate> tick(long nowMs, WeightPolicy policy, Thresholds thresholds) {
        // 1) score everyone
        Map<String, ScoreResult> scores = new LinkedHashMap<>();
        for (ParticipantBelief b : participants.values()) {
            scores.put(b.participantId(), b.score(nowMs, policy, thresholds));
        }
        // 2) reclassify with separation vs the best OTHER participant
        List<VerdictUpdate> changes = new ArrayList<>();
        for (ParticipantBelief b : participants.values()) {
            ScoreResult r = scores.get(b.participantId());
            double separation = r.score() - maxOtherScore(scores, b.participantId());
            ParticipantState prev = b.state();
            ParticipantState next = b.reclassify(nowMs, separation, r, thresholds);
            if (next != prev) {
                changes.add(new VerdictUpdate(b.participantId(), r, prev, next, separation));
            }
        }
        return changes;
    }

    /** All participant ids currently tracked in this meeting. */
    public java.util.Set<String> participantIds() {
        return java.util.Set.copyOf(participants.keySet());
    }

    /** Current classified state of a participant (UNSPECIFIED if unknown). */
    public ParticipantState stateOf(String participantId) {
        ParticipantBelief b = participants.get(participantId);
        return b == null ? ParticipantState.PARTICIPANT_STATE_UNSPECIFIED : b.state();
    }

    /** Score a participant at an instant without reclassifying (for inspection/tests). */
    public ScoreResult scoreOf(String participantId, long nowMs, WeightPolicy policy, Thresholds thresholds) {
        ParticipantBelief b = participants.get(participantId);
        return b == null ? null : b.score(nowMs, policy, thresholds);
    }

    private static double maxOtherScore(Map<String, ScoreResult> scores, String self) {
        double max = 0.0;
        for (Map.Entry<String, ScoreResult> e : scores.entrySet()) {
            if (!e.getKey().equals(self)) {
                max = Math.max(max, e.getValue().score());
            }
        }
        return max;
    }
}
