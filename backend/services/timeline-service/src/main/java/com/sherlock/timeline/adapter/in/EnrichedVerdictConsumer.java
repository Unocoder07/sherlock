package com.sherlock.timeline.adapter.in;

import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.explanation.v1.EnrichedVerdict;
import com.sherlock.timeline.application.TimelineAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code verdict.enriched} and feeds each verdict to the timeline
 * projector. Consuming the enriched stream (rather than raw confidence.updates)
 * keeps English rendering in one place — the Explanation Engine — so timeline
 * entries carry human text for free. At-least-once delivery is deduped on the
 * envelope {@code event_id}; poison messages are logged and dropped (doc 03 §4/§7).
 */
@Component
public class EnrichedVerdictConsumer {

    private static final Logger log = LoggerFactory.getLogger(EnrichedVerdictConsumer.class);

    private final TimelineAppService service;
    private final IdempotencyGuard guard = new IdempotencyGuard(100_000);

    public EnrichedVerdictConsumer(TimelineAppService service) {
        this.service = service;
    }

    @KafkaListener(topics = "verdict.enriched", groupId = "timeline-service")
    public void onMessage(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return; // duplicate delivery
            }
            EnrichedVerdict verdict = EnrichedVerdict.parseFrom(env.getPayload());
            service.record(verdict);
        } catch (Exception e) {
            log.warn("Dropping unparseable enriched verdict: {}", e.getMessage());
        }
    }
}
