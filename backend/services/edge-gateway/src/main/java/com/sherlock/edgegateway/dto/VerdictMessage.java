package com.sherlock.edgegateway.dto;

import java.util.List;

/**
 * Browser-facing verdict payload pushed over WebSocket. This is a DTO — deliberately
 * separate from the {@code EnrichedVerdict} proto so the wire contract to the UI can
 * evolve independently of the event schema (doc 08 §6). {@code state}/{@code previousState}
 * are the clean labels (already stripped upstream by the Explanation Engine), and each
 * reason now carries a rendered English {@code text} plus a {@code headline} summary.
 */
public record VerdictMessage(
        String meetingId,
        String participantId,
        double score,
        String state,
        String previousState,
        double separation,
        String headline,
        List<Reason> reasons,
        long occurredAtMs) {

    /**
     * One evidence-type's contribution, rendered to English by the Explanation Engine.
     * The numeric {@code magnitude} is retained so the UI can size bars / order emphasis.
     */
    public record Reason(
            String text,
            String evidenceType,
            int polarity,
            double magnitude) {
    }
}
