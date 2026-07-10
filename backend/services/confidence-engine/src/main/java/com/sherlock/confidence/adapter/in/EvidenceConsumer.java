package com.sherlock.confidence.adapter.in;

import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.common.kafka.IdempotencyGuard;
import com.sherlock.confidence.application.ConfidenceService;
import com.sherlock.confidence.domain.Evidence;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.evidence.v1.EvidenceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes normalized {@code evidence.events} and feeds them into the belief.
 * Delivery is at-least-once, so re-deliveries are deduped on the envelope
 * {@code event_id} (doc 03 §4). The evidence weight/polarity are NOT taken from
 * the wire — they are applied from the engine's own {@link com.sherlock.confidence.domain.WeightPolicy}
 * so tuning stays in one place (doc 05 §9); only observation-specific magnitude +
 * reliability + event-time come from the record.
 */
@Component
public class EvidenceConsumer {

    private static final Logger log = LoggerFactory.getLogger(EvidenceConsumer.class);

    private final ConfidenceService service;
    private final IdempotencyGuard guard = new IdempotencyGuard(100_000);

    public EvidenceConsumer(ConfidenceService service) {
        this.service = service;
    }

    @KafkaListener(topics = "evidence.events", groupId = "confidence-engine")
    public void onMessage(byte[] value) {
        try {
            EventEnvelope env = EnvelopeCodec.parse(value);
            if (guard.seen(env.getEventId())) {
                return; // duplicate delivery
            }
            EvidenceRecord rec = EvidenceRecord.parseFrom(env.getPayload());
            Evidence evidence = new Evidence(
                    rec.getParticipantId(),
                    rec.getEvidenceType(),
                    rec.getSource(),
                    rec.getRawValue(),      // magnitude — already normalized 0..1 by Fusion
                    rec.getReliability(),
                    rec.getOccurredAtMs());
            service.apply(env.getMeetingId(), evidence);
        } catch (Exception e) {
            // Poison message: log and drop rather than block the partition (doc 03 §7).
            log.warn("Dropping unparseable evidence event: {}", e.getMessage());
        }
    }
}
