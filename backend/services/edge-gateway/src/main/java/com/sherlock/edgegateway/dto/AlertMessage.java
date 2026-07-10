package com.sherlock.edgegateway.dto;

/**
 * Browser-facing alert pushed over WebSocket. Mirrors the Notification Service's
 * {@code Notification} proto, kept as a DTO so the UI contract can evolve
 * independently of the event schema.
 */
public record AlertMessage(
        String meetingId,
        String participantId,
        String notificationId,
        String severity,      // INFO | WARNING | CRITICAL
        String rule,
        String title,
        String message,
        String state,
        long occurredAtMs) {
}
