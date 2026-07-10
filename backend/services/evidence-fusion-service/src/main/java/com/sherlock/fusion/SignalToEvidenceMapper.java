package com.sherlock.fusion;

import com.sherlock.contracts.anchor.v1.ConsistencySignal;
import com.sherlock.contracts.evidence.v1.EvidenceRecord;
import com.sherlock.contracts.evidence.v1.EvidenceSource;
import com.sherlock.contracts.evidence.v1.EvidenceType;
import com.sherlock.contracts.meeting.v1.ParticipantJoined;
import com.sherlock.contracts.meeting.v1.ScreenShareChanged;
import com.sherlock.contracts.signals.v1.AudioSignal;
import com.sherlock.contracts.signals.v1.VideoSignal;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The anti-corruption layer (doc 02 §4): turns messy per-modality AI signals into
 * the uniform {@link EvidenceRecord} model the Confidence Engine consumes. Pure
 * and stateless — one method per signal family, each a closed mapping so adding a
 * new signal source means adding one adapter here and nothing in the scorer
 * (open/closed, doc 05 §9).
 *
 * <p>Base weight + polarity per evidence type follow the doc 05 §2 hierarchy. The
 * observation-specific {@code raw_value} (magnitude) and {@code reliability} are
 * derived from each signal's quality fields (detector confidence, SNR, score).
 */
public final class SignalToEvidenceMapper {

    private record Wp(double weight, int polarity) {
    }

    private static final Map<EvidenceType, Wp> WP = new EnumMap<>(EvidenceType.class);

    static {
        WP.put(EvidenceType.EVIDENCE_TYPE_AV_BINDING, new Wp(0.30, +1));
        WP.put(EvidenceType.EVIDENCE_TYPE_FACE_MATCH, new Wp(0.28, +1));
        WP.put(EvidenceType.EVIDENCE_TYPE_VOICE_MATCH, new Wp(0.24, +1));
        WP.put(EvidenceType.EVIDENCE_TYPE_DOMINANCE, new Wp(0.15, +1));
        WP.put(EvidenceType.EVIDENCE_TYPE_FACE_PRESENT, new Wp(0.12, +1));
        WP.put(EvidenceType.EVIDENCE_TYPE_SCREEN_SHARE, new Wp(0.10, +1));
        WP.put(EvidenceType.EVIDENCE_TYPE_MEETING_EVENT, new Wp(0.05, +1));
        WP.put(EvidenceType.EVIDENCE_TYPE_METADATA_NAME, new Wp(0.03, +1));
        WP.put(EvidenceType.EVIDENCE_TYPE_ANCHOR_MISMATCH, new Wp(0.30, -1));
        WP.put(EvidenceType.EVIDENCE_TYPE_FACE_CHANGED, new Wp(0.30, -1));
        WP.put(EvidenceType.EVIDENCE_TYPE_VOICE_CHANGED, new Wp(0.25, -1));
        WP.put(EvidenceType.EVIDENCE_TYPE_AV_BINDING_BROKEN, new Wp(0.28, -1));
        WP.put(EvidenceType.EVIDENCE_TYPE_MULTIPLE_PRESENCE, new Wp(0.10, -1));
    }

    // ── video.signals ────────────────────────────────────────────────────────
    public List<EvidenceRecord> mapVideo(VideoSignal s, long occurredAtMs) {
        return switch (s.getType()) {
            case VIDEO_SIGNAL_TYPE_FACE_PRESENT -> List.of(record(
                    s.getMeetingId(), s.getParticipantId(), EvidenceType.EVIDENCE_TYPE_FACE_PRESENT,
                    EvidenceSource.EVIDENCE_SOURCE_VIDEO,
                    clamp01(s.getDetectionConf()),
                    reliabilityFrom(s.getQuality(), s.getDetectionConf()),
                    occurredAtMs));
            case VIDEO_SIGNAL_TYPE_MULTIPLE_FACES -> List.of(record(
                    s.getMeetingId(), s.getParticipantId(), EvidenceType.EVIDENCE_TYPE_MULTIPLE_PRESENCE,
                    EvidenceSource.EVIDENCE_SOURCE_VIDEO,
                    1.0, clamp01(s.getDetectionConf()), occurredAtMs));
            // FACE_ABSENT (camera off) and FACE_EMBEDDING (raw vector for the anchor)
            // produce no evidence here — absence is handled by decay, and the anchor
            // consumes embeddings and re-emits consistency signals.
            default -> List.of();
        };
    }

    // ── audio.signals ────────────────────────────────────────────────────────
    public List<EvidenceRecord> mapAudio(AudioSignal s, long occurredAtMs) {
        return switch (s.getType()) {
            case AUDIO_SIGNAL_TYPE_SPEAKING -> List.of(record(
                    s.getMeetingId(), s.getParticipantId(), EvidenceType.EVIDENCE_TYPE_DOMINANCE,
                    EvidenceSource.EVIDENCE_SOURCE_AUDIO,
                    clamp01(s.getDominance()), reliabilityFromSnr(s.getSnr()), occurredAtMs));
            case AUDIO_SIGNAL_TYPE_MULTIPLE_SPEAKERS -> List.of(record(
                    s.getMeetingId(), s.getParticipantId(), EvidenceType.EVIDENCE_TYPE_MULTIPLE_PRESENCE,
                    EvidenceSource.EVIDENCE_SOURCE_AUDIO,
                    1.0, reliabilityFromSnr(s.getSnr()), occurredAtMs));
            default -> List.of(); // SILENCE / VOICE_EMBEDDING → no evidence
        };
    }

    // ── identity.anchor consistency signals ──────────────────────────────────
    public List<EvidenceRecord> mapConsistency(ConsistencySignal s) {
        EvidenceType type = switch (s.getType()) {
            case CONSISTENCY_TYPE_FACE_CONSISTENT_WITH_ANCHOR -> EvidenceType.EVIDENCE_TYPE_FACE_MATCH;
            case CONSISTENCY_TYPE_VOICE_CONSISTENT_WITH_ANCHOR -> EvidenceType.EVIDENCE_TYPE_VOICE_MATCH;
            case CONSISTENCY_TYPE_AV_BINDING -> EvidenceType.EVIDENCE_TYPE_AV_BINDING;
            case CONSISTENCY_TYPE_ANCHOR_MISMATCH -> EvidenceType.EVIDENCE_TYPE_ANCHOR_MISMATCH;
            case CONSISTENCY_TYPE_AV_BINDING_BROKEN -> EvidenceType.EVIDENCE_TYPE_AV_BINDING_BROKEN;
            case CONSISTENCY_TYPE_FACE_CHANGED -> EvidenceType.EVIDENCE_TYPE_FACE_CHANGED;
            case CONSISTENCY_TYPE_VOICE_CHANGED -> EvidenceType.EVIDENCE_TYPE_VOICE_CHANGED;
            default -> null;
        };
        if (type == null) {
            return List.of();
        }
        return List.of(record(
                s.getMeetingId(), s.getParticipantId(), type, EvidenceSource.EVIDENCE_SOURCE_ANCHOR,
                clamp01(s.getScore()), clamp01(s.getReliability()), s.getOccurredAtMs()));
    }

    // ── meeting.events ───────────────────────────────────────────────────────
    public List<EvidenceRecord> mapParticipantJoined(ParticipantJoined e, long occurredAtMs) {
        return List.of(record(
                e.getMeetingId(), e.getParticipantId(), EvidenceType.EVIDENCE_TYPE_MEETING_EVENT,
                EvidenceSource.EVIDENCE_SOURCE_MEETING, 1.0, 1.0, occurredAtMs));
    }

    public List<EvidenceRecord> mapScreenShare(ScreenShareChanged e, long occurredAtMs) {
        if (!e.getSharing()) {
            return List.of(); // stopping a share removes support via decay, not a new record
        }
        return List.of(record(
                e.getMeetingId(), e.getParticipantId(), EvidenceType.EVIDENCE_TYPE_SCREEN_SHARE,
                EvidenceSource.EVIDENCE_SOURCE_MEETING, 1.0, 1.0, occurredAtMs));
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static EvidenceRecord record(String meetingId, String participantId, EvidenceType type,
                                         EvidenceSource source, double rawValue, double reliability,
                                         long occurredAtMs) {
        Wp wp = WP.getOrDefault(type, new Wp(0.0, 0));
        return EvidenceRecord.newBuilder()
                .setMeetingId(meetingId)
                .setParticipantId(participantId)
                .setEvidenceType(type)
                .setSource(source)
                .setRawValue((float) rawValue)
                .setWeight((float) wp.weight())
                .setReliability((float) reliability)
                .setPolarity(wp.polarity())
                .setOccurredAtMs(occurredAtMs)
                .build();
    }

    private static double reliabilityFrom(double quality, double detectionConf) {
        double q = quality > 0 ? quality : detectionConf;
        return clamp01(q);
    }

    /** SNR → reliability. The simulator emits SNR already normalized to 0..1. */
    private static double reliabilityFromSnr(double snr) {
        return clamp01(snr <= 1.0 ? snr : snr / 40.0);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
