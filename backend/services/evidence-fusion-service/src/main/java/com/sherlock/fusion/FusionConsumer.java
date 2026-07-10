package com.sherlock.fusion;

import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.EventPublisher;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.contracts.anchor.v1.ConsistencySignal;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.evidence.v1.EvidenceRecord;
import com.sherlock.contracts.meeting.v1.ParticipantJoined;
import com.sherlock.contracts.meeting.v1.ScreenShareChanged;
import com.sherlock.contracts.signals.v1.AudioSignal;
import com.sherlock.contracts.signals.v1.VideoSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fan-in of every upstream stream (video/audio signals, anchor consistency,
 * meeting events), normalized through {@link SignalToEvidenceMapper} and emitted
 * as {@code evidence.events}. Stateless: one message in → zero-or-more evidence
 * records out. At-least-once delivery is deduped on the envelope {@code event_id}.
 */
@Component
public class FusionConsumer {

    private static final Logger log = LoggerFactory.getLogger(FusionConsumer.class);
    private static final String EVIDENCE_TOPIC = "evidence.events";

    private final EventPublisher publisher;
    private final SignalToEvidenceMapper mapper = new SignalToEvidenceMapper();
    private final IdempotencyGuard guard = new IdempotencyGuard(200_000);

    public FusionConsumer(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @KafkaListener(topics = "video.signals", groupId = "evidence-fusion")
    public void onVideo(byte[] value) {
        forEach(value, (env, payload) ->
                mapper.mapVideo(VideoSignal.parseFrom(payload), env.getOccurredAtMs()));
    }

    @KafkaListener(topics = "audio.signals", groupId = "evidence-fusion")
    public void onAudio(byte[] value) {
        forEach(value, (env, payload) ->
                mapper.mapAudio(AudioSignal.parseFrom(payload), env.getOccurredAtMs()));
    }

    @KafkaListener(topics = "identity.anchor", groupId = "evidence-fusion")
    public void onAnchor(byte[] value) {
        forEach(value, (env, payload) -> {
            // The topic carries both lifecycle (AnchorEvent) and consistency signals;
            // only the consistency signals become evidence (lifecycle → Timeline/Notify).
            if ("sherlock.anchor.v1.ConsistencySignal".equals(env.getEventType())) {
                return mapper.mapConsistency(ConsistencySignal.parseFrom(payload));
            }
            return List.of();
        });
    }

    @KafkaListener(topics = "meeting.events", groupId = "evidence-fusion")
    public void onMeeting(byte[] value) {
        forEach(value, (env, payload) -> switch (env.getEventType()) {
            case "sherlock.meeting.v1.ParticipantJoined" ->
                    mapper.mapParticipantJoined(ParticipantJoined.parseFrom(payload), env.getOccurredAtMs());
            case "sherlock.meeting.v1.ScreenShareChanged" ->
                    mapper.mapScreenShare(ScreenShareChanged.parseFrom(payload), env.getOccurredAtMs());
            default -> List.of();
        });
    }

    // ── shared decode → map → publish path ───────────────────────────────────
    @FunctionalInterface
    private interface Mapping {
        List<EvidenceRecord> apply(EventEnvelope env, com.google.protobuf.ByteString payload) throws Exception;
    }

    private void forEach(byte[] value, Mapping mapping) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return; // duplicate delivery
            }
            for (EvidenceRecord rec : mapping.apply(env, env.getPayload())) {
                publisher.publish(EVIDENCE_TOPIC, rec.getMeetingId(), rec.getParticipantId(),
                        rec.getOccurredAtMs(), rec);
            }
        } catch (Exception e) {
            log.warn("Dropping unmappable signal ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
