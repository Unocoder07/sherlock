package com.sherlock.meeting.adapter.in.rest;

import com.sherlock.meeting.adapter.in.rest.dto.MeetingDtos.CreateMeetingRequest;
import com.sherlock.meeting.adapter.in.rest.dto.MeetingDtos.MeetingResponse;
import com.sherlock.meeting.adapter.in.rest.dto.MeetingDtos.ParticipantJoinRequest;
import com.sherlock.meeting.adapter.in.rest.dto.MeetingDtos.ParticipantResponse;
import com.sherlock.meeting.adapter.in.rest.dto.MeetingDtos.ScreenShareRequest;
import com.sherlock.meeting.application.dto.Commands;
import com.sherlock.meeting.application.port.in.ManageMeetingUseCase;
import com.sherlock.meeting.application.port.in.ManageParticipantsUseCase;
import com.sherlock.meeting.application.port.in.QueryMeetingUseCase;
import com.sherlock.meeting.domain.model.Meeting;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Thin HTTP adapter over the meeting use cases. Controllers hold NO business
 * logic — they map DTOs to commands, delegate to a use-case port, and map the
 * result back. All orchestration/invariants live in the application/domain layers.
 */
@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final ManageMeetingUseCase manageMeeting;
    private final ManageParticipantsUseCase manageParticipants;
    private final QueryMeetingUseCase queryMeeting;

    public MeetingController(ManageMeetingUseCase manageMeeting,
                             ManageParticipantsUseCase manageParticipants,
                             QueryMeetingUseCase queryMeeting) {
        this.manageMeeting = manageMeeting;
        this.manageParticipants = manageParticipants;
        this.queryMeeting = queryMeeting;
    }

    @PostMapping
    public ResponseEntity<MeetingResponse> create(@RequestBody CreateMeetingRequest req,
                                                  UriComponentsBuilder uri) {
        Meeting m = manageMeeting.createMeeting(
                new Commands.CreateMeeting(req.title(), req.externalRef(), req.scheduledAt()));
        URI location = uri.path("/api/v1/meetings/{id}").buildAndExpand(m.id().asString()).toUri();
        return ResponseEntity.created(location).body(MeetingRestMapper.toResponse(m));
    }

    @GetMapping("/{id}")
    public MeetingResponse get(@PathVariable String id) {
        return MeetingRestMapper.toResponse(queryMeeting.getMeeting(id));
    }

    @GetMapping
    public List<MeetingResponse> list() {
        return queryMeeting.listMeetings().stream().map(MeetingRestMapper::toResponse).toList();
    }

    @PostMapping("/{id}/start")
    public MeetingResponse start(@PathVariable String id) {
        return MeetingRestMapper.toResponse(manageMeeting.startMeeting(id));
    }

    @PostMapping("/{id}/stop")
    public MeetingResponse stop(@PathVariable String id) {
        return MeetingRestMapper.toResponse(manageMeeting.endMeeting(id));
    }

    @GetMapping("/{id}/participants")
    public List<ParticipantResponse> participants(@PathVariable String id) {
        return queryMeeting.getMeeting(id).participants().stream()
                .map(MeetingRestMapper::toResponse).toList();
    }

    @PostMapping("/{id}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipantResponse join(@PathVariable String id, @RequestBody ParticipantJoinRequest req) {
        Meeting m = manageParticipants.participantJoined(new Commands.ParticipantJoin(
                id, req.displayName(), req.platformUserId(), req.cameraOn()));
        // Return the just-joined participant (last in roster).
        return MeetingRestMapper.toResponse(m.participants().get(m.participants().size() - 1));
    }

    @PostMapping("/{id}/participants/{pid}/leave")
    public MeetingResponse leave(@PathVariable String id, @PathVariable String pid) {
        return MeetingRestMapper.toResponse(
                manageParticipants.participantLeft(new Commands.ParticipantLeave(id, pid)));
    }

    @PostMapping("/{id}/participants/{pid}/screen-share")
    public MeetingResponse screenShare(@PathVariable String id, @PathVariable String pid,
                                       @RequestBody ScreenShareRequest req) {
        return MeetingRestMapper.toResponse(
                manageParticipants.setScreenShare(new Commands.SetScreenShare(id, pid, req.sharing())));
    }
}
