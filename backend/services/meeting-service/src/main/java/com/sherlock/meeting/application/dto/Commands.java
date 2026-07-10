package com.sherlock.meeting.application.dto;

import java.time.Instant;

/**
 * Application-layer command inputs. These are transport-agnostic: the REST
 * adapter maps HTTP request DTOs into these, keeping controllers thin and the
 * use cases independent of the web layer.
 */
public final class Commands {

    private Commands() {
    }

    public record CreateMeeting(
            String title,
            String externalRef,
            Instant scheduledAt
    ) {
    }

    public record ParticipantJoin(
            String meetingId,
            String displayName,
            String platformUserId,
            boolean cameraOn
    ) {
    }

    public record ParticipantLeave(
            String meetingId,
            String participantId
    ) {
    }

    public record SetScreenShare(
            String meetingId,
            String participantId,
            boolean sharing
    ) {
    }
}
