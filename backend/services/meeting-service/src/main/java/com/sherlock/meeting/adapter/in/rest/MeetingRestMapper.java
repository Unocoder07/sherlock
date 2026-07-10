package com.sherlock.meeting.adapter.in.rest;

import com.sherlock.meeting.adapter.in.rest.dto.MeetingDtos.MeetingResponse;
import com.sherlock.meeting.adapter.in.rest.dto.MeetingDtos.ParticipantResponse;
import com.sherlock.meeting.domain.model.Meeting;
import com.sherlock.meeting.domain.model.Participant;

/** Maps the domain aggregate to REST response DTOs (outbound direction only). */
final class MeetingRestMapper {

    private MeetingRestMapper() {
    }

    static MeetingResponse toResponse(Meeting m) {
        return new MeetingResponse(
                m.id().asString(),
                m.title(),
                m.externalRef(),
                m.state().name(),
                m.scheduledAt(),
                m.startedAt(),
                m.endedAt(),
                m.participants().stream().map(MeetingRestMapper::toResponse).toList());
    }

    static ParticipantResponse toResponse(Participant p) {
        return new ParticipantResponse(
                p.id().asString(),
                p.displayName(),
                p.platformUserId(),
                p.joinedAt(),
                p.leftAt(),
                p.isCameraOn(),
                p.isScreenSharing(),
                p.isPresent());
    }
}
