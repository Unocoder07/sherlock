package com.sherlock.confidence.domain;

import java.util.List;

/**
 * The outcome of scoring one participant at an instant: the bounded belief plus
 * the derived signals the state classifier needs, and the ranked per-type
 * contributions the Explanation Engine will later turn into English (doc 05 §10).
 *
 * @param contributions           signed per-evidence-type contributions (for explainability)
 * @param corroboratingModalities distinct positive modalities currently live (VIDEO/AUDIO/ANCHOR)
 * @param positiveSupport         summed magnitude of live positive contributions
 * @param negativeBiometric       summed magnitude of live negative biometric contributions (mismatch/change/broken)
 * @param hasSwitchSignal         a live FACE_CHANGED / VOICE_CHANGED is present
 * @param anchorReliability       reliability of the strongest live anchor-consistency signal (0 = anchor not engaged)
 * @param anyLiveSignal           any evidence still above the decay floor
 */
public record ScoreResult(
        double score,
        List<Contribution> contributions,
        int corroboratingModalities,
        double positiveSupport,
        double negativeBiometric,
        boolean hasSwitchSignal,
        double anchorReliability,
        boolean anyLiveSignal) {

    /** Per evidence-type signed contribution to the score (doc 05 §3). */
    public record Contribution(String evidenceType, double value, double weight, int polarity) {
    }
}
