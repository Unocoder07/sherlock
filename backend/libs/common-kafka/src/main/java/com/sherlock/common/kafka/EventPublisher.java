package com.sherlock.common.kafka;

import com.google.protobuf.Message;
import com.sherlock.contracts.common.v1.EventEnvelope;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;

/**
 * Thin publisher over {@link KafkaTemplate} that wraps a typed protobuf payload
 * in an {@link EventEnvelope} and sends it keyed by {@code meetingId} (so every
 * event for a meeting keeps per-partition ordering).
 *
 * <p>Derived services (Fusion, Anchor) publish directly through this; the
 * Confidence Engine uses it from its outbox relay. The {@code producer} tag
 * (e.g. {@code "confidence-engine@0.1.0"}) identifies the source in the envelope.
 */
public class EventPublisher {

    private static final int SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, byte[]> template;
    private final String producer;
    private final Clock clock;

    public EventPublisher(KafkaTemplate<String, byte[]> template, String producer, Clock clock) {
        this.template = template;
        this.producer = producer;
        this.clock = clock;
    }

    /**
     * Wrap and send a payload. Returns the envelope's {@code event_id} for logging
     * / idempotency correlation.
     */
    public String publish(String topic,
                          String meetingId,
                          String participantId,
                          long occurredAtMs,
                          Message payload) {
        EventEnvelope envelope = EnvelopeCodec.wrap(
                producer, SCHEMA_VERSION, meetingId, participantId, occurredAtMs, clock.millis(), payload);
        template.send(topic, meetingId, envelope.toByteArray());
        return envelope.getEventId();
    }
}
