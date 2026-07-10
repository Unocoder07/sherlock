package com.sherlock.meeting.domain.model;

import java.util.UUID;

/** Strongly-typed participant identifier. See {@link MeetingId} for rationale. */
public record ParticipantId(UUID value) {

    public ParticipantId {
        if (value == null) {
            throw new IllegalArgumentException("ParticipantId value must not be null");
        }
    }

    public static ParticipantId newId() {
        return new ParticipantId(UUID.randomUUID());
    }

    public static ParticipantId of(String raw) {
        return new ParticipantId(UUID.fromString(raw));
    }

    public String asString() {
        return value.toString();
    }
}
