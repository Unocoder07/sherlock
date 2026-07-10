package com.sherlock.anchor.adapter;

import com.sherlock.anchor.domain.MockAnchorEngine;
import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.EventPublisher;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.signals.v1.AudioSignal;
import com.sherlock.contracts.signals.v1.VideoSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds video/audio signals into the {@link MockAnchorEngine} and publishes its
 * emissions (anchor lifecycle + consistency signals) to {@code identity.anchor}.
 * At-least-once delivery is deduped on the envelope {@code event_id}.
 */
@Component
public class AnchorConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnchorConsumer.class);
    private static final String ANCHOR_TOPIC = "identity.anchor";

    private final EventPublisher publisher;
    private final MockAnchorEngine engine = new MockAnchorEngine();
    private final IdempotencyGuard guard = new IdempotencyGuard(200_000);

    public AnchorConsumer(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @KafkaListener(topics = "video.signals", groupId = "identity-anchor")
    public void onVideo(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return;
            }
            VideoSignal sig = VideoSignal.parseFrom(env.getPayload());
            for (MockAnchorEngine.Emission e : engine.onVideo(sig, env.getOccurredAtMs())) {
                publish(env.getMeetingId(), e);
            }
        } catch (Exception e) {
            log.warn("Dropping video signal: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "audio.signals", groupId = "identity-anchor")
    public void onAudio(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return;
            }
            AudioSignal sig = AudioSignal.parseFrom(env.getPayload());
            for (MockAnchorEngine.Emission e : engine.onAudio(sig, env.getOccurredAtMs())) {
                publish(env.getMeetingId(), e);
            }
        } catch (Exception e) {
            log.warn("Dropping audio signal: {}", e.getMessage());
        }
    }

    private void publish(String meetingId, MockAnchorEngine.Emission e) {
        publisher.publish(ANCHOR_TOPIC, meetingId, e.participantId(), e.occurredAtMs(), e.payload());
    }
}
