package com.sherlock.edgegateway.adapter.in;

import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.notification.v1.Notification;
import com.sherlock.edgegateway.application.AlertBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code notifications} (the Notification Service's output) and fans each
 * alert out to the meeting's alert topic for the dashboard banner. Its own
 * {@code groupId} keeps the gateway's copy independent; re-deliveries are deduped
 * on the envelope {@code event_id} and poison messages are logged and dropped.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final AlertBroadcastService broadcaster;
    private final IdempotencyGuard guard = new IdempotencyGuard(100_000);

    public NotificationConsumer(AlertBroadcastService broadcaster) {
        this.broadcaster = broadcaster;
    }

    @KafkaListener(topics = "notifications", groupId = "edge-gateway-alert")
    public void onMessage(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return; // duplicate delivery
            }
            Notification notification = Notification.parseFrom(env.getPayload());
            broadcaster.broadcast(notification);
        } catch (Exception e) {
            log.warn("Dropping unparseable notification: {}", e.getMessage());
        }
    }
}
