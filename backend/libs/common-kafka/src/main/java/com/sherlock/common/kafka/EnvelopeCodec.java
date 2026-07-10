package com.sherlock.common.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.sherlock.contracts.common.v1.EventEnvelope;

import java.util.UUID;

/**
 * Builds and parses the canonical {@link EventEnvelope} that wraps EVERY message
 * on the Sherlock bus. This is the single place the envelope shape is applied,
 * generalizing the per-service mapper introduced in M1 (meeting-service) so the
 * three M2 services and the simulator share one implementation.
 *
 * <p>The typed payload is carried as serialized bytes and discriminated by
 * {@code event_type} (its protobuf full name), so one uniform envelope spans all
 * topics while each event keeps its own schema.
 */
public final class EnvelopeCodec {

    private EnvelopeCodec() {
    }

    /**
     * Wrap a typed payload in an envelope. {@code eventType} is derived from the
     * payload's protobuf descriptor, and a fresh UUID becomes the idempotency key.
     */
    public static EventEnvelope wrap(String producer,
                                     int schemaVersion,
                                     String meetingId,
                                     String participantId,
                                     long occurredAtMs,
                                     long emittedAtMs,
                                     Message payload) {
        return EventEnvelope.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(payload.getDescriptorForType().getFullName())
                .setSchemaVersion(schemaVersion)
                .setMeetingId(nz(meetingId))
                .setParticipantId(nz(participantId))
                .setOccurredAtMs(occurredAtMs)
                .setEmittedAtMs(emittedAtMs)
                .setProducer(nz(producer))
                .setTraceId("")
                .setPayload(payload.toByteString())
                .build();
    }

    /** Parse envelope bytes off the wire. */
    public static EventEnvelope parse(byte[] bytes) throws InvalidProtocolBufferException {
        return EventEnvelope.parseFrom(bytes);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
