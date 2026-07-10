package com.sherlock.notification.adapter.in;

import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.confidence.v1.ConfidenceUpdate;
import com.sherlock.notification.application.NotificationAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code confidence.updates} and runs each through the alert rules. Reads
 * the raw verdict (not the enriched one) because rules key off {@code state}/
 * {@code previous_state}. At-least-once delivery is deduped on the envelope
 * {@code event_id}; poison messages are logged and dropped (doc 03 §4/§7).
 */
@Component
public class ConfidenceUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ConfidenceUpdateConsumer.class);

    private final NotificationAppService service;
    private final IdempotencyGuard guard = new IdempotencyGuard(100_000);

    public ConfidenceUpdateConsumer(NotificationAppService service) {
        this.service = service;
    }

    @KafkaListener(topics = "confidence.updates", groupId = "notification-service")
    public void onMessage(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return; // duplicate delivery
            }
            ConfidenceUpdate update = ConfidenceUpdate.parseFrom(env.getPayload());
            service.handle(update);
        } catch (Exception e) {
            log.warn("Dropping unparseable confidence update: {}", e.getMessage());
        }
    }
}
