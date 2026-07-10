package com.sherlock.meeting.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * REST request/response DTOs. Kept separate from domain + JPA models (DTO pattern)
 * so the HTTP contract can evolve independently and entities never leak over the wire.
 */
public final class MeetingDtos {

    private MeetingDtos() {
    }

    // ── Requests ──
    public record CreateMeetingRequest(
            String title,
            String externalRef,
            Instant scheduledAt
    ) {
    }

    public record ParticipantJoinRequest(
            String displayName,
            String platformUserId,
            boolean cameraOn
    ) {
    }

    public record ScreenShareRequest(
            @NotNull Boolean sharing
    ) {
    }

    // ── Responses ──
    public record ParticipantResponse(
            String id,
            String displayName,
            String platformUserId,
            Instant joinedAt,
            Instant leftAt,
            boolean cameraOn,
            boolean screenSharing,
            boolean present
    ) {
    }

    public record MeetingResponse(
            String id,
            String title,
            String externalRef,
            String state,
            Instant scheduledAt,
            Instant startedAt,
            Instant endedAt,
            List<ParticipantResponse> participants
    ) {
    }
}
