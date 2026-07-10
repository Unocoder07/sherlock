package com.sherlock.fusion;

import com.sherlock.contracts.anchor.v1.ConsistencySignal;
import com.sherlock.contracts.anchor.v1.ConsistencyType;
import com.sherlock.contracts.evidence.v1.EvidenceRecord;
import com.sherlock.contracts.evidence.v1.EvidenceSource;
import com.sherlock.contracts.evidence.v1.EvidenceType;
import com.sherlock.contracts.meeting.v1.ParticipantJoined;
import com.sherlock.contracts.signals.v1.AudioSignal;
import com.sherlock.contracts.signals.v1.AudioSignalType;
import com.sherlock.contracts.signals.v1.VideoSignal;
import com.sherlock.contracts.signals.v1.VideoSignalType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks in the signal → normalized-evidence mapping rules (doc 05 §2). */
class SignalToEvidenceMapperTest {

    private final SignalToEvidenceMapper mapper = new SignalToEvidenceMapper();

    @Test
    void facePresent_mapsTo_positiveFacePresentEvidence() {
        VideoSignal s = VideoSignal.newBuilder()
                .setMeetingId("m1").setParticipantId("p1")
                .setType(VideoSignalType.VIDEO_SIGNAL_TYPE_FACE_PRESENT)
                .setDetectionConf(0.9f).setQuality(0.8f)
                .build();

        EvidenceRecord r = mapper.mapVideo(s, 1000L).get(0);
        assertThat(r.getEvidenceType()).isEqualTo(EvidenceType.EVIDENCE_TYPE_FACE_PRESENT);
        assertThat(r.getSource()).isEqualTo(EvidenceSource.EVIDENCE_SOURCE_VIDEO);
        assertThat(r.getPolarity()).isEqualTo(1);
        assertThat(r.getReliability()).isEqualTo(0.8f);      // from quality
        assertThat(r.getOccurredAtMs()).isEqualTo(1000L);
    }

    @Test
    void multipleFaces_mapsTo_negativeAmbiguityEvidence() {
        VideoSignal s = VideoSignal.newBuilder()
                .setMeetingId("m1").setParticipantId("p1")
                .setType(VideoSignalType.VIDEO_SIGNAL_TYPE_MULTIPLE_FACES)
                .setDetectionConf(0.7f)
                .build();

        EvidenceRecord r = mapper.mapVideo(s, 0L).get(0);
        assertThat(r.getEvidenceType()).isEqualTo(EvidenceType.EVIDENCE_TYPE_MULTIPLE_PRESENCE);
        assertThat(r.getPolarity()).isEqualTo(-1);
    }

    @Test
    void faceEmbedding_producesNoEvidence_itFeedsTheAnchor() {
        VideoSignal s = VideoSignal.newBuilder()
                .setType(VideoSignalType.VIDEO_SIGNAL_TYPE_FACE_EMBEDDING)
                .build();
        assertThat(mapper.mapVideo(s, 0L)).isEmpty();
    }

    @Test
    void speaking_mapsTo_dominanceEvidence_withSnrReliability() {
        AudioSignal s = AudioSignal.newBuilder()
                .setMeetingId("m1").setParticipantId("p1")
                .setType(AudioSignalType.AUDIO_SIGNAL_TYPE_SPEAKING)
                .setDominance(0.85f).setSnr(0.9f)
                .build();

        EvidenceRecord r = mapper.mapAudio(s, 0L).get(0);
        assertThat(r.getEvidenceType()).isEqualTo(EvidenceType.EVIDENCE_TYPE_DOMINANCE);
        assertThat(r.getSource()).isEqualTo(EvidenceSource.EVIDENCE_SOURCE_AUDIO);
        assertThat(r.getRawValue()).isEqualTo(0.85f);
        assertThat(r.getReliability()).isEqualTo(0.9f);
    }

    @Test
    void anchorConsistency_positive_mapsToFaceMatchFromAnchor() {
        ConsistencySignal s = ConsistencySignal.newBuilder()
                .setMeetingId("m1").setParticipantId("p1")
                .setType(ConsistencyType.CONSISTENCY_TYPE_FACE_CONSISTENT_WITH_ANCHOR)
                .setScore(0.82f).setReliability(0.9f).setOccurredAtMs(5L)
                .build();

        EvidenceRecord r = mapper.mapConsistency(s).get(0);
        assertThat(r.getEvidenceType()).isEqualTo(EvidenceType.EVIDENCE_TYPE_FACE_MATCH);
        assertThat(r.getSource()).isEqualTo(EvidenceSource.EVIDENCE_SOURCE_ANCHOR);
        assertThat(r.getPolarity()).isEqualTo(1);
        assertThat(r.getRawValue()).isEqualTo(0.82f);
    }

    @Test
    void anchorMismatch_mapsTo_negativeEvidence() {
        ConsistencySignal s = ConsistencySignal.newBuilder()
                .setMeetingId("m1").setParticipantId("p1")
                .setType(ConsistencyType.CONSISTENCY_TYPE_ANCHOR_MISMATCH)
                .setScore(0.8f).setReliability(0.9f)
                .build();

        EvidenceRecord r = mapper.mapConsistency(s).get(0);
        assertThat(r.getEvidenceType()).isEqualTo(EvidenceType.EVIDENCE_TYPE_ANCHOR_MISMATCH);
        assertThat(r.getPolarity()).isEqualTo(-1);
    }

    @Test
    void participantJoined_mapsTo_lowWeightMeetingEvidence() {
        ParticipantJoined e = ParticipantJoined.newBuilder()
                .setMeetingId("m1").setParticipantId("p1").setDisplayName("iPhone")
                .build();

        EvidenceRecord r = mapper.mapParticipantJoined(e, 3L).get(0);
        assertThat(r.getEvidenceType()).isEqualTo(EvidenceType.EVIDENCE_TYPE_MEETING_EVENT);
        assertThat(r.getSource()).isEqualTo(EvidenceSource.EVIDENCE_SOURCE_MEETING);
        assertThat(r.getWeight()).isEqualTo(0.05f); // lowest tier by design
    }
}
