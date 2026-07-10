package com.sherlock.meeting.domain.model;

import java.util.UUID;

/**
 * Strongly-typed meeting identifier. Using a value object instead of a raw
 * {@link UUID}/{@link String} prevents mixing up ids of different entities at
 * compile time and gives the domain a clear vocabulary.
 */
public record MeetingId(UUID value) {

    public MeetingId {
        if (value == null) {
            throw new IllegalArgumentException("MeetingId value must not be null");
        }
    }

    public static MeetingId newId() {
        return new MeetingId(UUID.randomUUID());
    }

    public static MeetingId of(String raw) {
        return new MeetingId(UUID.fromString(raw));
    }

    public String asString() {
        return value.toString();
    }
}
