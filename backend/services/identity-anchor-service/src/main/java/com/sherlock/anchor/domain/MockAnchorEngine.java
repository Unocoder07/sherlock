package com.sherlock.anchor.domain;

import com.google.protobuf.Message;
import com.sherlock.contracts.anchor.v1.AnchorEvent;
import com.sherlock.contracts.anchor.v1.AnchorLifecycle;
import com.sherlock.contracts.anchor.v1.ConsistencySignal;
import com.sherlock.contracts.anchor.v1.ConsistencyType;
import com.sherlock.contracts.signals.v1.AudioSignal;
import com.sherlock.contracts.signals.v1.VideoSignal;
import com.sherlock.contracts.signals.v1.VideoSignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A MOCK Identity Anchor Service (M2). It simulates the zero-knowledge cold-start
 * lifecycle — {@code OBSERVING → ANCHORING → LOCKED} — and the per-window
 * consistency signals, WITHOUT any ML: instead of clustering real embeddings it
 * treats each signal's {@code embedding_ref} as an opaque identity token. The
 * output contract ({@code identity.anchor}: {@link AnchorEvent} lifecycle +
 * {@link ConsistencySignal}) is exactly what the real M6.5 service will emit, so
 * it drops in behind the same wire contract.
 *
 * <p>Scenario detection is faithful to self-anchored mode (doc 02 §3.1):
 * <ul>
 *   <li>tokens stay consistent + lips move with voice → LOCK, positive consistency → IDENTIFIED;</li>
 *   <li>anchored face token changes after lock → FACE_CHANGED (candidate switch);</li>
 *   <li>face present but lips silent while a voice is active → AV_BINDING_BROKEN (off-screen relay);</li>
 *   <li>face absent + voice consistent → carried by voice (camera-off), no false break.</li>
 * </ul>
 */
public final class MockAnchorEngine {

    private static final int OBSERVE_WINDOWS = 2;  // windows before ANCHORING
    private static final int LOCK_WINDOWS = 4;     // windows before LOCKED
    private static final double LIP_SPEAKING = 0.5;
    private static final double LIP_SILENT = 0.3;

    /** One emission destined for {@code identity.anchor}. */
    public record Emission(String participantId, long occurredAtMs, Message payload) {
    }

    /** Per-meeting mock anchor state. */
    private static final class Anchor {
        AnchorLifecycle lifecycle = AnchorLifecycle.ANCHOR_LIFECYCLE_OBSERVING;
        String anchoredPid;
        String anchorFaceToken;
        String anchorVoiceToken;
        int windows;
        boolean facePresent;
        double lastLip;
    }

    private final Map<String, Anchor> anchors = new ConcurrentHashMap<>();

    // ── video ────────────────────────────────────────────────────────────────
    public List<Emission> onVideo(VideoSignal v, long occurredAtMs) {
        Anchor a = anchors.computeIfAbsent(v.getMeetingId(), k -> new Anchor());
        List<Emission> out = new ArrayList<>();

        if (v.getType() == VideoSignalType.VIDEO_SIGNAL_TYPE_FACE_ABSENT) {
            a.facePresent = false;
            return out; // camera off — nothing to assert; decay carries the belief
        }
        a.facePresent = true;
        a.lastLip = v.getLipActivity();
        String faceToken = v.getEmbeddingRef();

        // Pick the first observed participant as the anchor candidate.
        if (a.anchoredPid == null) {
            a.anchoredPid = v.getParticipantId();
            a.anchorFaceToken = faceToken;
        }
        advanceLifecycle(a, v.getMeetingId(), occurredAtMs, out);

        if (a.lifecycle == AnchorLifecycle.ANCHOR_LIFECYCLE_LOCKED
                && v.getParticipantId().equals(a.anchoredPid)) {
            if (faceToken.equals(a.anchorFaceToken)) {
                out.add(consistency(v.getMeetingId(), a.anchoredPid,
                        ConsistencyType.CONSISTENCY_TYPE_FACE_CONSISTENT_WITH_ANCHOR,
                        magnitude(v.getDetectionConf(), v.getQuality()), 0.9, occurredAtMs));
            } else {
                // face embedding drifted from the locked anchor → candidate switch
                a.lifecycle = AnchorLifecycle.ANCHOR_LIFECYCLE_DRIFT;
                out.add(anchorEvent(v.getMeetingId(), a, AnchorLifecycle.ANCHOR_LIFECYCLE_DRIFT, 0.4, occurredAtMs));
                out.add(consistency(v.getMeetingId(), a.anchoredPid,
                        ConsistencyType.CONSISTENCY_TYPE_FACE_CHANGED, 0.85, 0.9, occurredAtMs));
            }
        }
        return out;
    }

    // ── audio ────────────────────────────────────────────────────────────────
    public List<Emission> onAudio(AudioSignal s, long occurredAtMs) {
        Anchor a = anchors.computeIfAbsent(s.getMeetingId(), k -> new Anchor());
        List<Emission> out = new ArrayList<>();

        // Seed the anchor's voice token the first time we hear the anchored participant.
        if (s.getParticipantId().equals(a.anchoredPid) && a.anchorVoiceToken == null
                && !s.getEmbeddingRef().isEmpty()) {
            a.anchorVoiceToken = s.getEmbeddingRef();
        }

        if (a.lifecycle != AnchorLifecycle.ANCHOR_LIFECYCLE_LOCKED
                || !s.getParticipantId().equals(a.anchoredPid) || !s.getVoiceActive()) {
            return out;
        }

        double rel = Math.max(0.0, Math.min(1.0, s.getSnr()));
        // Voice consistency vs the anchored voice token.
        if (a.anchorVoiceToken == null || s.getEmbeddingRef().equals(a.anchorVoiceToken)) {
            out.add(consistency(s.getMeetingId(), a.anchoredPid,
                    ConsistencyType.CONSISTENCY_TYPE_VOICE_CONSISTENT_WITH_ANCHOR,
                    clamp01(s.getDominance()), rel, occurredAtMs));
        } else {
            out.add(consistency(s.getMeetingId(), a.anchoredPid,
                    ConsistencyType.CONSISTENCY_TYPE_VOICE_CHANGED, 0.85, rel, occurredAtMs));
        }

        // Audio-visual binding: only meaningful when a face IS on camera.
        if (a.facePresent) {
            if (a.lastLip >= LIP_SPEAKING) {
                out.add(consistency(s.getMeetingId(), a.anchoredPid,
                        ConsistencyType.CONSISTENCY_TYPE_AV_BINDING,
                        Math.min(a.lastLip, clamp01(s.getDominance())), 0.85, occurredAtMs));
            } else if (a.lastLip <= LIP_SILENT) {
                // voice active but the visible face isn't moving → someone off-screen answers
                out.add(consistency(s.getMeetingId(), a.anchoredPid,
                        ConsistencyType.CONSISTENCY_TYPE_AV_BINDING_BROKEN, 0.85, 0.85, occurredAtMs));
            }
        }
        return out;
    }

    // ── lifecycle progression ────────────────────────────────────────────────
    private void advanceLifecycle(Anchor a, String meetingId, long occurredAtMs, List<Emission> out) {
        a.windows++;
        if (a.lifecycle == AnchorLifecycle.ANCHOR_LIFECYCLE_OBSERVING && a.windows >= OBSERVE_WINDOWS) {
            a.lifecycle = AnchorLifecycle.ANCHOR_LIFECYCLE_ANCHORING;
            out.add(anchorEvent(meetingId, a, AnchorLifecycle.ANCHOR_LIFECYCLE_ANCHORING, 0.5, occurredAtMs));
        } else if (a.lifecycle == AnchorLifecycle.ANCHOR_LIFECYCLE_ANCHORING && a.windows >= LOCK_WINDOWS) {
            a.lifecycle = AnchorLifecycle.ANCHOR_LIFECYCLE_LOCKED;
            out.add(anchorEvent(meetingId, a, AnchorLifecycle.ANCHOR_LIFECYCLE_LOCKED, 0.9, occurredAtMs));
        }
    }

    // ── builders ─────────────────────────────────────────────────────────────
    private static Emission consistency(String meetingId, String pid, ConsistencyType type,
                                        double score, double reliability, long occurredAtMs) {
        ConsistencySignal sig = ConsistencySignal.newBuilder()
                .setMeetingId(meetingId)
                .setParticipantId(pid)
                .setType(type)
                .setScore((float) clamp01(score))
                .setReliability((float) clamp01(reliability))
                .setOccurredAtMs(occurredAtMs)
                .build();
        return new Emission(pid, occurredAtMs, sig);
    }

    private static Emission anchorEvent(String meetingId, Anchor a, AnchorLifecycle lifecycle,
                                        double stability, long occurredAtMs) {
        AnchorEvent ev = AnchorEvent.newBuilder()
                .setMeetingId(meetingId)
                .setAnchoredParticipantId(a.anchoredPid == null ? "" : a.anchoredPid)
                .setLifecycle(lifecycle)
                .setMode("SELF")
                .setAvBindingScore((float) stability)
                .setStability((float) stability)
                .setOccurredAtMs(occurredAtMs)
                .build();
        return new Emission(a.anchoredPid, occurredAtMs, ev);
    }

    private static double magnitude(double detectionConf, double quality) {
        double m = quality > 0 ? quality : detectionConf;
        return clamp01(m);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
