package com.sherlock.explanation.adapter.in;

import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.confidence.v1.ConfidenceUpdate;
import com.sherlock.explanation.adapter.out.EnrichedVerdictPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code confidence.updates}, renders each verdict into an enriched
 * verdict, and republishes it. Delivery is at-least-once, so re-deliveries are
 * deduped on the envelope {@code event_id} and poison messages are logged and
 * dropped rather than blocking the partition (doc 03 §4/§7) — mirrors the
 * gateway/confidence consumers.
 */
@Component
public class ConfidenceUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ConfidenceUpdateConsumer.class);

    private final EnrichedVerdictPublisher publisher;
    private final IdempotencyGuard guard = new IdempotencyGuard(100_000);

    public ConfidenceUpdateConsumer(EnrichedVerdictPublisher publisher) {
        this.publisher = publisher;
    }

    @KafkaListener(topics = "confidence.updates", groupId = "explanation-engine")
    public void onMessage(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return; // duplicate delivery
            }
            ConfidenceUpdate update = ConfidenceUpdate.parseFrom(env.getPayload());
            publisher.publish(update);
        } catch (Exception e) {
            log.warn("Dropping unparseable confidence update: {}", e.getMessage());
        }
    }
}
