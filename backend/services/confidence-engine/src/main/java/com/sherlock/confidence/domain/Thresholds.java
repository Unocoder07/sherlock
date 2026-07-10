package com.sherlock.confidence.domain;

/**
 * Hysteresis thresholds and guards for the state classifier (doc 05 §5, doc 06 §3).
 * Asymmetric enter/leave bounds prevent flicker — the #1 source of false positives.
 *
 * @param enterIdentified   score to ENTER IDENTIFIED (hard)
 * @param leaveIdentified   score to LEAVE IDENTIFIED (easier — hysteresis)
 * @param uncertainFloor    below this (with an anchor engaged) the participant is still ANCHORING
 * @param minSeparation     required score(top) − score(second) to crown a winner
 * @param dwellSeconds      identify condition must hold this long before IDENTIFIED
 * @param minCorroboratingModalities independent modalities that must agree for IDENTIFIED
 * @param anchorLockReliability      anchor-consistency reliability that counts as "anchor locked"
 * @param signalFloor       decayed contribution magnitude below which evidence is considered dead
 * @param negativeDominanceFloor     net-negative biometric magnitude that trips PROXY_SUSPECTED
 */
public record Thresholds(
        double enterIdentified,
        double leaveIdentified,
        double uncertainFloor,
        double minSeparation,
        long dwellSeconds,
        int minCorroboratingModalities,
        double anchorLockReliability,
        double signalFloor,
        double negativeDominanceFloor) {

    /** Defaults from doc 05 §5 / doc 06 §3. */
    public static Thresholds defaults() {
        return new Thresholds(
                0.85,   // enterIdentified
                0.70,   // leaveIdentified
                0.40,   // uncertainFloor
                0.20,   // minSeparation
                8,      // dwellSeconds
                2,      // minCorroboratingModalities
                0.60,   // anchorLockReliability
                0.05,   // signalFloor
                0.12);  // negativeDominanceFloor
    }
}
