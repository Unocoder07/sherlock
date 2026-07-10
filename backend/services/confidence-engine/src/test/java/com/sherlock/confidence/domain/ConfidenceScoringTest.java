package com.sherlock.confidence.domain;

import com.sherlock.contracts.confidence.v1.ParticipantState;
import com.sherlock.contracts.evidence.v1.EvidenceSource;
import com.sherlock.contracts.evidence.v1.EvidenceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-domain tests for the intellectual core — the scoring model and the
 * hysteresis state machine. No Spring, no infra. These lock in the behaviours
 * the whole system's correctness depends on: the doc 05 §10 worked example and
 * the doc 05 §7 edge cases (cold start, proxy, switch, camera-off, metadata-only).
 */
class ConfidenceScoringTest {

    private static final WeightPolicy POLICY = WeightPolicy.defaults();
    private static final Thresholds T = Thresholds.defaults();
    private static final long DWELL_MS = T.dwellSeconds() * 1000;

    private final MeetingBelief meeting = new MeetingBelief("m1");

    private void ev(String pid, EvidenceType type, EvidenceSource src, double mag, double rel, long t) {
        meeting.apply(new Evidence(pid, type, src, mag, rel, t));
    }

    /** A full, corroborated identifying bundle (anchor + video + audio + behaviour) at time t. */
    private void applyIdentifying(String pid, long t) {
        ev(pid, EvidenceType.EVIDENCE_TYPE_AV_BINDING, EvidenceSource.EVIDENCE_SOURCE_ANCHOR, 0.90, 0.90, t);
        ev(pid, EvidenceType.EVIDENCE_TYPE_FACE_MATCH, EvidenceSource.EVIDENCE_SOURCE_ANCHOR, 0.88, 0.90, t);
        ev(pid, EvidenceType.EVIDENCE_TYPE_VOICE_MATCH, EvidenceSource.EVIDENCE_SOURCE_ANCHOR, 0.84, 0.90, t);
        ev(pid, EvidenceType.EVIDENCE_TYPE_FACE_PRESENT, EvidenceSource.EVIDENCE_SOURCE_VIDEO, 0.96, 0.90, t);
        ev(pid, EvidenceType.EVIDENCE_TYPE_DOMINANCE, EvidenceSource.EVIDENCE_SOURCE_AUDIO, 0.91, 0.90, t);
        ev(pid, EvidenceType.EVIDENCE_TYPE_SCREEN_SHARE, EvidenceSource.EVIDENCE_SOURCE_MEETING, 1.0, 0.80, t);
        ev(pid, EvidenceType.EVIDENCE_TYPE_METADATA_NAME, EvidenceSource.EVIDENCE_SOURCE_MEETING, 1.0, 1.0, t);
    }

    private ParticipantState tickState(String pid, long now) {
        meeting.tick(now, POLICY, T);
        return meeting.stateOf(pid);
    }

    // ── the worked example (doc 05 §10) ──────────────────────────────────────

    @Test
    void workedExample_reachesIdentified_withHighScore() {
        applyIdentifying("john", 0);

        ScoreResult r = meeting.scoreOf("john", 0, POLICY, T);
        // Fresh, corroborated biometric evidence → high belief (~0.94 in the doc).
        assertThat(r.score()).isBetween(0.88, 0.99);
        assertThat(r.corroboratingModalities()).isGreaterThanOrEqualTo(2);

        // First tick sets the dwell clock (not yet sustained) → not IDENTIFIED.
        assertThat(tickState("john", 0)).isNotEqualTo(ParticipantState.PARTICIPANT_STATE_IDENTIFIED);
        // Keep the signal fresh and let the dwell window elapse → IDENTIFIED.
        applyIdentifying("john", DWELL_MS + 1000);
        assertThat(tickState("john", DWELL_MS + 1000)).isEqualTo(ParticipantState.PARTICIPANT_STATE_IDENTIFIED);
    }

    // ── zero-knowledge cold start (doc 05 §1.1, doc 06) ──────────────────────

    @Test
    void coldStart_progresses_observing_then_identified() {
        // Phase 1: only raw presence/voice, NO anchor consistency yet → OBSERVING.
        ev("john", EvidenceType.EVIDENCE_TYPE_FACE_PRESENT, EvidenceSource.EVIDENCE_SOURCE_VIDEO, 0.9, 0.9, 0);
        ev("john", EvidenceType.EVIDENCE_TYPE_DOMINANCE, EvidenceSource.EVIDENCE_SOURCE_AUDIO, 0.9, 0.9, 0);
        assertThat(tickState("john", 0)).isEqualTo(ParticipantState.PARTICIPANT_STATE_OBSERVING);

        // Phase 2: anchor engages + full corroboration, sustained past the dwell → IDENTIFIED.
        applyIdentifying("john", 1000);
        tickState("john", 1000);
        applyIdentifying("john", DWELL_MS + 2000);
        assertThat(tickState("john", DWELL_MS + 2000)).isEqualTo(ParticipantState.PARTICIPANT_STATE_IDENTIFIED);
    }

    // ── proxy (doc 05 §7) ────────────────────────────────────────────────────

    @Test
    void proxy_negativeBiometricDominates_flagsProxySuspected() {
        // Present person contradicts the anchor: mismatch + broken A/V binding.
        ev("proxy", EvidenceType.EVIDENCE_TYPE_ANCHOR_MISMATCH, EvidenceSource.EVIDENCE_SOURCE_ANCHOR, 0.85, 0.9, 0);
        ev("proxy", EvidenceType.EVIDENCE_TYPE_AV_BINDING_BROKEN, EvidenceSource.EVIDENCE_SOURCE_ANCHOR, 0.80, 0.9, 0);
        ev("proxy", EvidenceType.EVIDENCE_TYPE_FACE_PRESENT, EvidenceSource.EVIDENCE_SOURCE_VIDEO, 0.9, 0.9, 0);

        assertThat(tickState("proxy", 0)).isEqualTo(ParticipantState.PARTICIPANT_STATE_PROXY_SUSPECTED);
    }

    // ── candidate switch (doc 05 §7) ─────────────────────────────────────────

    @Test
    void switch_afterIdentified_flagsCandidateSwitched() {
        applyIdentifying("john", 0);
        tickState("john", 0);
        applyIdentifying("john", DWELL_MS + 1000);
        assertThat(tickState("john", DWELL_MS + 1000)).isEqualTo(ParticipantState.PARTICIPANT_STATE_IDENTIFIED);

        // A face-embedding drift after identification → someone else on camera.
        ev("john", EvidenceType.EVIDENCE_TYPE_FACE_CHANGED, EvidenceSource.EVIDENCE_SOURCE_ANCHOR, 0.8, 0.9, DWELL_MS + 2000);
        assertThat(tickState("john", DWELL_MS + 2000)).isEqualTo(ParticipantState.PARTICIPANT_STATE_CANDIDATE_SWITCHED);
    }

    // ── camera-off carries via voice; never a false proxy (doc 05 §7) ─────────

    @Test
    void cameraOff_doesNotFalselyFlipToProxyOrSwitch() {
        applyIdentifying("john", 0);
        tickState("john", 0);
        applyIdentifying("john", DWELL_MS + 1000);
        tickState("john", DWELL_MS + 1000);

        // Camera off: only voice keeps refreshing; face evidence goes stale and decays.
        long later = DWELL_MS + 60_000;
        ev("john", EvidenceType.EVIDENCE_TYPE_VOICE_MATCH, EvidenceSource.EVIDENCE_SOURCE_ANCHOR, 0.84, 0.9, later);
        ev("john", EvidenceType.EVIDENCE_TYPE_DOMINANCE, EvidenceSource.EVIDENCE_SOURCE_AUDIO, 0.9, 0.9, later);
        ParticipantState s = tickState("john", later);

        assertThat(s).isNotIn(
                ParticipantState.PARTICIPANT_STATE_PROXY_SUSPECTED,
                ParticipantState.PARTICIPANT_STATE_CANDIDATE_SWITCHED);
    }

    // ── metadata cannot cross the line (doc 05 §2, §6) ───────────────────────

    @Test
    void metadataAlone_neverIdentifies() {
        for (long t = 0; t <= DWELL_MS + 5000; t += 2000) {
            ev("john", EvidenceType.EVIDENCE_TYPE_METADATA_NAME, EvidenceSource.EVIDENCE_SOURCE_MEETING, 1.0, 1.0, t);
            assertThat(tickState("john", t)).isNotEqualTo(ParticipantState.PARTICIPANT_STATE_IDENTIFIED);
        }
    }
}
