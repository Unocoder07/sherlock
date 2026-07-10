package com.sherlock.edgegateway.adapter.in;

import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.timeline.v1.TimelineEntry;
import com.sherlock.edgegateway.application.TimelineBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code timeline.events} (the Timeline Service's output) and fans each
 * entry out to the meeting's timeline topic. Its own {@code groupId} keeps the
 * gateway's copy independent; re-deliveries are deduped on the envelope
 * {@code event_id} and poison messages are logged and dropped (doc 03 §4/§7).
 */
@Component
public class TimelineEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TimelineEventConsumer.class);

    private final TimelineBroadcastService broadcaster;
    private final IdempotencyGuard guard = new IdempotencyGuard(100_000);

    public TimelineEventConsumer(TimelineBroadcastService broadcaster) {
        this.broadcaster = broadcaster;
    }

    @KafkaListener(topics = "timeline.events", groupId = "edge-gateway-timeline")
    public void onMessage(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return; // duplicate delivery
            }
            TimelineEntry entry = TimelineEntry.parseFrom(env.getPayload());
            broadcaster.broadcast(entry);
        } catch (Exception e) {
            log.warn("Dropping unparseable timeline entry: {}", e.getMessage());
        }
    }
}
