package com.sherlock.common.kafka;

import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.evidence.v1.EvidenceRecord;
import com.sherlock.contracts.evidence.v1.EvidenceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trips the envelope codec and locks in the idempotency guard's dedupe. */
class EnvelopeCodecTest {

    @Test
    void wrap_thenParse_preservesEnvelopeAndTypedPayload() throws Exception {
        EvidenceRecord payload = EvidenceRecord.newBuilder()
                .setMeetingId("m1")
                .setParticipantId("p1")
                .setEvidenceType(EvidenceType.EVIDENCE_TYPE_FACE_MATCH)
                .setRawValue(0.88f)
                .build();

        EventEnvelope env = EnvelopeCodec.wrap(
                "test@0.1.0", 1, "m1", "p1", 1000L, 2000L, payload);

        // event_type is derived from the payload descriptor.
        assertThat(env.getEventType()).isEqualTo("sherlock.evidence.v1.EvidenceRecord");
        assertThat(env.getMeetingId()).isEqualTo("m1");
        assertThat(env.getParticipantId()).isEqualTo("p1");
        assertThat(env.getOccurredAtMs()).isEqualTo(1000L);
        assertThat(env.getEmittedAtMs()).isEqualTo(2000L);
        assertThat(env.getEventId()).isNotBlank();

        // Bytes survive a round-trip and the payload re-parses to the original.
        EventEnvelope reparsed = EnvelopeCodec.parse(env.toByteArray());
        EvidenceRecord back = EvidenceRecord.parseFrom(reparsed.getPayload());
        assertThat(back.getRawValue()).isEqualTo(0.88f);
        assertThat(back.getEvidenceType()).isEqualTo(EvidenceType.EVIDENCE_TYPE_FACE_MATCH);
    }

    @Test
    void idempotencyGuard_reportsSecondSightingAsSeen() {
        IdempotencyGuard guard = new IdempotencyGuard(128);
        assertThat(guard.seen("e1")).isFalse();  // first time → new
        assertThat(guard.seen("e1")).isTrue();   // again → duplicate
        assertThat(guard.seen("e2")).isFalse();
    }
}
