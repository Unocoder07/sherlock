package com.sherlock.confidence.domain;

import com.sherlock.contracts.confidence.v1.ParticipantState;
import com.sherlock.contracts.evidence.v1.EvidenceSource;
import com.sherlock.contracts.evidence.v1.EvidenceType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-participant belief — the stateful heart of the engine. It keeps the
 * most-recent observation per evidence type and, at any instant, recomputes the
 * score as a pure function of {@code (stored evidence, now)} (doc 03 §4: updates
 * are time-based, not blind increments, so replay is deterministic/idempotent).
 *
 * <p>Scoring (doc 05 §3–§4): each live observation contributes
 * {@code polarity·weight·reliability·magnitude·decay}; contributions sum, a
 * corroboration bonus is added when ≥2 independent modalities agree, and the sum
 * is mapped through {@code sigmoid(bias + gain·Σ)} to a bounded belief. The state
 * is then classified with hysteresis + separation + dwell guards (doc 05 §5,
 * doc 06 §3).
 */
public final class ParticipantBelief {

    /** Most-recent observation for one evidence type. */
    private record Observation(EvidenceSource source, double magnitude, double reliability, long occurredAtMs) {
    }

    /** Negative biometric types whose dominance implies a proxy (not mere ambiguity). */
    private static final Set<EvidenceType> NEGATIVE_BIOMETRIC = EnumSet.of(
            EvidenceType.EVIDENCE_TYPE_ANCHOR_MISMATCH,
            EvidenceType.EVIDENCE_TYPE_AV_BINDING_BROKEN,
            EvidenceType.EVIDENCE_TYPE_FACE_CHANGED,
            EvidenceType.EVIDENCE_TYPE_VOICE_CHANGED);

    /** Modalities that count toward corroboration (metadata/meeting excluded by design). */
    private static final Set<EvidenceSource> BIOMETRIC_MODALITIES = EnumSet.of(
            EvidenceSource.EVIDENCE_SOURCE_VIDEO,
            EvidenceSource.EVIDENCE_SOURCE_AUDIO,
            EvidenceSource.EVIDENCE_SOURCE_ANCHOR);

    private final String participantId;
    private final Map<EvidenceType, Observation> latest = new EnumMap<>(EvidenceType.class);

    private ParticipantState state = ParticipantState.PARTICIPANT_STATE_UNSPECIFIED;
    private boolean present = true;
    private boolean everIdentified = false;
    private boolean everHadSignal = false;
    private Long identifyEligibleSinceMs = null;

    public ParticipantBelief(String participantId) {
        this.participantId = participantId;
    }

    public String participantId() {
        return participantId;
    }

    public ParticipantState state() {
        return state;
    }

    /** Record the newest observation for its type (last-writer-wins per type). */
    public void apply(Evidence e) {
        latest.put(e.type(), new Observation(e.source(), e.magnitude(), e.reliability(), e.occurredAtMs()));
    }

    /** Mark the participant as having left; freezes the belief on the next classify. */
    public void markLeft() {
        this.present = false;
    }

    /**
     * Score the belief at instant {@code nowMs} (doc 05 §3–§4). Pure w.r.t. stored
     * state — does not mutate the belief.
     */
    public ScoreResult score(long nowMs, WeightPolicy policy, Thresholds thresholds) {
        double sum = 0.0;
        double positiveSupport = 0.0;
        double negativeBiometric = 0.0;
        double anchorReliability = 0.0;
        boolean hasSwitchSignal = false;
        boolean anyLive = false;
        Set<EvidenceSource> positiveModalities = EnumSet.noneOf(EvidenceSource.class);
        List<ScoreResult.Contribution> contributions = new ArrayList<>();

        for (Map.Entry<EvidenceType, Observation> entry : latest.entrySet()) {
            EvidenceType type = entry.getKey();
            Observation obs = entry.getValue();
            WeightPolicy.Weight w = policy.weightFor(type);

            double decay = Scoring.decay(nowMs - obs.occurredAtMs(), w.halfLifeSeconds());
            double c = Scoring.contribution(w.polarity(), w.weight(), obs.reliability(), obs.magnitude(), decay);
            sum += c;
            contributions.add(new ScoreResult.Contribution(type.name(), c, w.weight(), w.polarity()));

            double liveMag = Math.abs(c);
            if (liveMag < thresholds.signalFloor()) {
                continue; // decayed to nothing — not a live signal
            }
            anyLive = true;

            if (w.polarity() > 0) {
                positiveSupport += liveMag;
                if (BIOMETRIC_MODALITIES.contains(obs.source())) {
                    positiveModalities.add(obs.source());
                }
                if (obs.source() == EvidenceSource.EVIDENCE_SOURCE_ANCHOR) {
                    anchorReliability = Math.max(anchorReliability, obs.reliability() * decay);
                }
            } else if (w.polarity() < 0) {
                if (NEGATIVE_BIOMETRIC.contains(type)) {
                    negativeBiometric += liveMag;
                }
                if (type == EvidenceType.EVIDENCE_TYPE_FACE_CHANGED
                        || type == EvidenceType.EVIDENCE_TYPE_VOICE_CHANGED) {
                    hasSwitchSignal = true;
                }
            }
        }

        if (positiveModalities.size() >= 2) {
            sum += policy.corroborationBonus(); // fusion > sum of parts (doc 05 §4)
        }

        double logit = policy.bias() + policy.gain() * sum;
        double score = Scoring.sigmoid(logit);

        return new ScoreResult(score, contributions, positiveModalities.size(),
                positiveSupport, negativeBiometric, hasSwitchSignal, anchorReliability, anyLive);
    }

    /**
     * Reclassify state from a fresh score + meeting-level separation, applying the
     * hysteresis/dwell/anchor guards (doc 06 §3). Mutates internal timers and the
     * current state; returns the (possibly unchanged) new state.
     */
    public ParticipantState reclassify(long nowMs, double separation, ScoreResult r, Thresholds t) {
        if (r.anyLiveSignal()) {
            everHadSignal = true;
        }
        ParticipantState next = classify(nowMs, separation, r, t);
        if (next == ParticipantState.PARTICIPANT_STATE_IDENTIFIED) {
            everIdentified = true;
        }
        this.state = next;
        return next;
    }

    private ParticipantState classify(long nowMs, double separation, ScoreResult r, Thresholds t) {
        if (!present) {
            return ParticipantState.PARTICIPANT_STATE_LEFT;
        }
        boolean identifiedNow = state == ParticipantState.PARTICIPANT_STATE_IDENTIFIED;

        // Candidate switch: a change signal after we had already identified someone.
        if (r.hasSwitchSignal() && (everIdentified || identifiedNow)) {
            return ParticipantState.PARTICIPANT_STATE_CANDIDATE_SWITCHED;
        }
        // Proxy: a present-but-contradicting biometric (anchor mismatch / broken A/V
        // binding / voice change) is materially active AND the belief is not strong.
        // Distinct from camera-off, which has NO negative biometric — only decay.
        if (r.negativeBiometric() >= t.negativeDominanceFloor()
                && r.score() < t.enterIdentified()) {
            return ParticipantState.PARTICIPANT_STATE_PROXY_SUSPECTED;
        }
        // No live signal: lost if we ever had one, else still observing cold.
        if (!r.anyLiveSignal()) {
            return everHadSignal
                    ? ParticipantState.PARTICIPANT_STATE_SIGNAL_LOST
                    : ParticipantState.PARTICIPANT_STATE_OBSERVING;
        }
        // Anchor not engaged yet → zero-knowledge observation.
        if (r.anchorReliability() <= 0.0) {
            return ParticipantState.PARTICIPANT_STATE_OBSERVING;
        }
        // Anchor engaging but belief still below the floor → provisional identity forming.
        if (r.score() < t.uncertainFloor()) {
            return ParticipantState.PARTICIPANT_STATE_ANCHORING;
        }

        boolean eligible = r.score() >= t.enterIdentified()
                && separation >= t.minSeparation()
                && r.corroboratingModalities() >= t.minCorroboratingModalities()
                && r.anchorReliability() >= t.anchorLockReliability();

        // Dwell: the identify condition must hold for a sustained window.
        if (eligible) {
            if (identifyEligibleSinceMs == null) {
                identifyEligibleSinceMs = nowMs;
            }
        } else {
            identifyEligibleSinceMs = null;
        }
        boolean sustained = identifyEligibleSinceMs != null
                && (nowMs - identifyEligibleSinceMs) >= t.dwellSeconds() * 1000;

        if (eligible && sustained) {
            return ParticipantState.PARTICIPANT_STATE_IDENTIFIED;
        }
        // Hysteresis: once identified, hold until the score drops below the lower bar.
        if (identifiedNow && r.score() >= t.leaveIdentified()) {
            return ParticipantState.PARTICIPANT_STATE_IDENTIFIED;
        }
        return ParticipantState.PARTICIPANT_STATE_UNCERTAIN;
    }
}
