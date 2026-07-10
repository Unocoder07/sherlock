package com.sherlock.edgegateway.dto;

/**
 * Browser-facing timeline entry pushed over WebSocket. Mirrors the Timeline
 * Service's {@code TimelineEntry} proto, kept as a DTO so the UI contract can
 * evolve independently of the event schema.
 */
public record TimelineMessage(
        String meetingId,
        String participantId,
        String entryId,
        String kind,          // STATE_TRANSITION | SCORE_INFLECTION
        String fromState,
        String toState,
        double score,
        String headline,
        String detail,
        long occurredAtMs) {
}
