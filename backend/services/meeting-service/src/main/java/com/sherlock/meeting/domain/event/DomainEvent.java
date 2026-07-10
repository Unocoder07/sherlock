package com.sherlock.meeting.domain.event;

import java.time.Instant;

/**
 * Marker for facts that have happened in the domain. Aggregates raise these;
 * the application layer drains them and hands them to the outbox for reliable
 * publication to Kafka. Domain events carry NO framework or transport concerns.
 */
public interface DomainEvent {

    /** Meeting this event belongs to (also the Kafka partition key downstream). */
    String meetingId();

    /** When the fact occurred (event time). */
    Instant occurredAt();
}
