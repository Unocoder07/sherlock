package com.sherlock.anchor.domain;

import com.sherlock.contracts.anchor.v1.AnchorEvent;
import com.sherlock.contracts.anchor.v1.AnchorLifecycle;
import com.sherlock.contracts.anchor.v1.ConsistencySignal;
import com.sherlock.contracts.anchor.v1.ConsistencyType;
import com.sherlock.contracts.signals.v1.AudioSignal;
import com.sherlock.contracts.signals.v1.AudioSignalType;
import com.sherlock.contracts.signals.v1.VideoSignal;
import com.sherlock.contracts.signals.v1.VideoSignalType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the mock anchor's lifecycle progression and the consistency signals it
 * emits per scenario (self-anchored detection, doc 02 §3.1 / doc 06).
 */
class MockAnchorEngineTest {

    private final MockAnchorEngine engine = new MockAnchorEngine();

    private VideoSignal face(String pid, String token, double lip, long t) {
        return VideoSignal.newBuilder()
                .setMeetingId("m1").setParticipantId(pid)
                .setType(VideoSignalType.VIDEO_SIGNAL_TYPE_FACE_PRESENT)
                .setEmbeddingRef(token).setDetectionConf(0.9f).setQuality(0.9f)
                .setLipActivity((float) lip)
                .setWindowStartMs(t).setWindowEndMs(t)
                .build();
    }

    private AudioSignal voice(String pid, String token, boolean active, double dominance, long t) {
        return AudioSignal.newBuilder()
                .setMeetingId("m1").setParticipantId(pid)
                .setType(AudioSignalType.AUDIO_SIGNAL_TYPE_SPEAKING)
                .setEmbeddingRef(token).setVoiceActive(active)
                .setDominance((float) dominance).setSnr(0.9f)
                .setWindowStartMs(t).setWindowEndMs(t)
                .build();
    }

    private List<Object> types(List<MockAnchorEngine.Emission> es) {
        List<Object> out = new ArrayList<>();
        for (MockAnchorEngine.Emission e : es) {
            if (e.payload() instanceof AnchorEvent ae) {
                out.add(ae.getLifecycle());
            } else if (e.payload() instanceof ConsistencySignal cs) {
                out.add(cs.getType());
            }
        }
        return out;
    }

    @Test
    void lifecycle_progresses_observing_anchoring_locked() {
        List<Object> seen = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            seen.addAll(types(engine.onVideo(face("john", "face:john", 0.8, i * 1000L), i * 1000L)));
        }
        assertThat(seen).contains(
                AnchorLifecycle.ANCHOR_LIFECYCLE_ANCHORING,
                AnchorLifecycle.ANCHOR_LIFECYCLE_LOCKED);
    }

    @Test
    void afterLock_consistentTokens_emitPositiveConsistency() {
        for (int i = 0; i < 5; i++) {
            engine.onVideo(face("john", "face:john", 0.8, i * 1000L), i * 1000L);
        }
        // Once locked, a matching face + active matching voice with moving lips.
        List<Object> v = types(engine.onVideo(face("john", "face:john", 0.8, 6000L), 6000L));
        List<Object> a = types(engine.onAudio(voice("john", "voice:john", true, 0.9, 6000L), 6000L));

        assertThat(v).contains(ConsistencyType.CONSISTENCY_TYPE_FACE_CONSISTENT_WITH_ANCHOR);
        assertThat(a).contains(
                ConsistencyType.CONSISTENCY_TYPE_VOICE_CONSISTENT_WITH_ANCHOR,
                ConsistencyType.CONSISTENCY_TYPE_AV_BINDING);
    }

    @Test
    void switch_faceTokenChangesAfterLock_emitsFaceChanged() {
        for (int i = 0; i < 5; i++) {
            engine.onVideo(face("john", "face:john", 0.8, i * 1000L), i * 1000L);
        }
        // A different face appears on the anchored participant's stream.
        List<Object> out = types(engine.onVideo(face("john", "face:impostor", 0.8, 6000L), 6000L));
        assertThat(out).contains(ConsistencyType.CONSISTENCY_TYPE_FACE_CHANGED);
    }

    @Test
    void relay_voiceActiveButLipsSilent_emitsAvBindingBroken() {
        for (int i = 0; i < 5; i++) {
            engine.onVideo(face("john", "face:john", 0.8, i * 1000L), i * 1000L);
        }
        // Face present but NOT moving (lips silent) while a voice is active → off-screen relay.
        engine.onVideo(face("john", "face:john", 0.0, 6000L), 6000L);
        List<Object> out = types(engine.onAudio(voice("john", "voice:offscreen", true, 0.9, 6000L), 6000L));
        assertThat(out).contains(ConsistencyType.CONSISTENCY_TYPE_AV_BINDING_BROKEN);
    }

    @Test
    void cameraOff_faceAbsentButVoiceConsistent_noFalseBinding() {
        for (int i = 0; i < 5; i++) {
            engine.onVideo(face("john", "face:john", 0.8, i * 1000L), i * 1000L);
        }
        // Camera off (FACE_ABSENT), voice still matches.
        engine.onVideo(VideoSignal.newBuilder()
                .setMeetingId("m1").setParticipantId("john")
                .setType(VideoSignalType.VIDEO_SIGNAL_TYPE_FACE_ABSENT)
                .build(), 6000L);
        List<Object> out = types(engine.onAudio(voice("john", "voice:john", true, 0.9, 6000L), 6000L));

        assertThat(out).contains(ConsistencyType.CONSISTENCY_TYPE_VOICE_CONSISTENT_WITH_ANCHOR);
        assertThat(out).doesNotContain(ConsistencyType.CONSISTENCY_TYPE_AV_BINDING_BROKEN);
    }
}
