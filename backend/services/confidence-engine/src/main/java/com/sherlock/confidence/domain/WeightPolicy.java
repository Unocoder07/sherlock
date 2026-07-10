package com.sherlock.confidence.domain;

import com.sherlock.contracts.evidence.v1.EvidenceType;

import java.util.EnumMap;
import java.util.Map;

/**
 * The externalized scoring policy (doc 05 §2, §9): base weight, polarity and
 * decay half-life per evidence type, plus the global calibration constants that
 * map the weighted-evidence sum into a bounded belief. Adding a signal is a
 * config change here — the scoring math (Scoring / ParticipantBelief) never
 * changes (Strategy-over-policy, open/closed).
 *
 * <p>Metadata sits an order of magnitude below the biometric signals by design,
 * so it can never alone cross the IDENTIFIED line (a first-class requirement).
 *
 * <p>The default constants are a sane starting calibration for M2; M7 tunes them
 * on recorded scenarios. {@code bias} pins "no evidence → low confidence"
 * ({@code sigmoid(bias)}), and {@code gain} scales the summed contributions into
 * log-odds so strong, corroborated evidence reaches the ~0.94 of the doc's
 * worked example (§10).
 */
public final class WeightPolicy {

    /** Per-type weight, polarity (+1 supports / −1 contradicts) and decay τ. */
    public record Weight(double weight, int polarity, double halfLifeSeconds) {
    }

    private final Map<EvidenceType, Weight> weights;
    private final double bias;
    private final double gain;
    private final double corroborationBonus;

    public WeightPolicy(Map<EvidenceType, Weight> weights,
                        double bias,
                        double gain,
                        double corroborationBonus) {
        this.weights = new EnumMap<>(weights);
        this.bias = bias;
        this.gain = gain;
        this.corroborationBonus = corroborationBonus;
    }

    public Weight weightFor(EvidenceType type) {
        return weights.getOrDefault(type, new Weight(0.0, 0, 45.0));
    }

    public double bias() {
        return bias;
    }

    public double gain() {
        return gain;
    }

    public double corroborationBonus() {
        return corroborationBonus;
    }

    /**
     * Default policy from the doc 05 §2 ranked signal hierarchy. Positive signals
     * support identity; negative signals (mismatch / change / broken binding /
     * ambiguity) actively pull the belief down and can trigger PROXY / SWITCHED.
     */
    public static WeightPolicy defaults() {
        Map<EvidenceType, Weight> w = new EnumMap<>(EvidenceType.class);
        // ── positive (support identity) ──
        w.put(EvidenceType.EVIDENCE_TYPE_AV_BINDING, new Weight(0.30, +1, 40.0)); // cold-start keystone
        w.put(EvidenceType.EVIDENCE_TYPE_FACE_MATCH, new Weight(0.28, +1, 45.0)); // vs anchor
        w.put(EvidenceType.EVIDENCE_TYPE_VOICE_MATCH, new Weight(0.24, +1, 45.0)); // vs anchor
        w.put(EvidenceType.EVIDENCE_TYPE_DOMINANCE, new Weight(0.15, +1, 30.0));
        w.put(EvidenceType.EVIDENCE_TYPE_FACE_PRESENT, new Weight(0.12, +1, 20.0));
        w.put(EvidenceType.EVIDENCE_TYPE_SCREEN_SHARE, new Weight(0.10, +1, 60.0));
        w.put(EvidenceType.EVIDENCE_TYPE_MEETING_EVENT, new Weight(0.05, +1, 120.0));
        w.put(EvidenceType.EVIDENCE_TYPE_METADATA_NAME, new Weight(0.03, +1, 300.0)); // lowest by design
        // ── negative (contradict identity) ──
        w.put(EvidenceType.EVIDENCE_TYPE_ANCHOR_MISMATCH, new Weight(0.30, -1, 45.0));
        w.put(EvidenceType.EVIDENCE_TYPE_FACE_CHANGED, new Weight(0.30, -1, 45.0));
        w.put(EvidenceType.EVIDENCE_TYPE_VOICE_CHANGED, new Weight(0.25, -1, 45.0));
        w.put(EvidenceType.EVIDENCE_TYPE_AV_BINDING_BROKEN, new Weight(0.28, -1, 40.0));
        w.put(EvidenceType.EVIDENCE_TYPE_MULTIPLE_PRESENCE, new Weight(0.10, -1, 20.0));

        // Calibration: sigmoid(bias) ≈ 0.12 baseline; gain lifts corroborated
        // biometric evidence to ~0.94 (doc 05 §10 worked example).
        return new WeightPolicy(w, -2.0, 4.5, 0.12);
    }
}
