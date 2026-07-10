package com.sherlock.meeting.domain.event;

import java.time.Instant;

/**
 * The concrete domain events raised by the {@link com.sherlock.meeting.domain.model.Meeting}
 * aggregate. Grouped in one file as small immutable records for readability; each maps to a
 * payload on the {@code meeting.events} Kafka topic (via the outbox + protobuf mapper).
 */
public final class MeetingEvents {

    private MeetingEvents() {
    }

    public record MeetingCreated(
            String meetingId,
            String title,
            String externalRef,
            Instant scheduledAt,
            Instant occurredAt
    ) implements DomainEvent {
    }

    public record MeetingStateChanged(
            String meetingId,
            String state,          // SCHEDULED | LIVE | ENDED
            Instant occurredAt
    ) implements DomainEvent {
    }

    public record ParticipantJoined(
            String meetingId,
            String participantId,
            String displayName,
            String platformUserId,
            boolean cameraOn,
            Instant occurredAt
    ) implements DomainEvent {
    }

    public record ParticipantLeft(
            String meetingId,
            String participantId,
            Instant occurredAt
    ) implements DomainEvent {
    }

    public record ScreenShareChanged(
            String meetingId,
            String participantId,
            boolean sharing,
            Instant occurredAt
    ) implements DomainEvent {
    }
}
