package com.sherlock.edgegateway.adapter.in;

import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.explanation.v1.EnrichedVerdict;
import com.sherlock.edgegateway.application.VerdictBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code verdict.enriched} (the Explanation Engine's output) and hands
 * each enriched verdict to the broadcaster. The gateway consumes the enriched
 * stream — not raw {@code confidence.updates} — so the browser receives English
 * reasons + a headline (doc 02 interaction matrix: Explanation → WS GW).
 *
 * <p>Uses its own {@code groupId} so the gateway gets an independent copy of the
 * stream; delivery is at-least-once, so re-deliveries are deduped on the envelope
 * {@code event_id} and poison messages are logged and dropped (doc 03 §4/§7).
 */
@Component
public class EnrichedVerdictConsumer {

    private static final Logger log = LoggerFactory.getLogger(EnrichedVerdictConsumer.class);

    private final VerdictBroadcastService broadcaster;
    private final IdempotencyGuard guard = new IdempotencyGuard(100_000);

    public EnrichedVerdictConsumer(VerdictBroadcastService broadcaster) {
        this.broadcaster = broadcaster;
    }

    @KafkaListener(topics = "verdict.enriched", groupId = "edge-gateway")
    public void onMessage(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return; // duplicate delivery
            }
            EnrichedVerdict update = EnrichedVerdict.parseFrom(env.getPayload());
            broadcaster.broadcast(update);
        } catch (Exception e) {
            log.warn("Dropping unparseable enriched verdict: {}", e.getMessage());
        }
    }
}
