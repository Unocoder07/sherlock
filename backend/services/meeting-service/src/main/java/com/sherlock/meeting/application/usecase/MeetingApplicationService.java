package com.sherlock.meeting.application.usecase;

import com.sherlock.meeting.application.MeetingNotFoundException;
import com.sherlock.meeting.application.dto.Commands;
import com.sherlock.meeting.application.port.in.ManageMeetingUseCase;
import com.sherlock.meeting.application.port.in.ManageParticipantsUseCase;
import com.sherlock.meeting.application.port.in.QueryMeetingUseCase;
import com.sherlock.meeting.application.port.out.DomainEventOutbox;
import com.sherlock.meeting.application.port.out.MeetingRepository;
import com.sherlock.meeting.domain.event.DomainEvent;
import com.sherlock.meeting.domain.model.Meeting;
import com.sherlock.meeting.domain.model.MeetingId;
import com.sherlock.meeting.domain.model.ParticipantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the meeting use cases. This is the ONLY place that combines a
 * domain mutation with persistence + outbox, all under one transaction.
 *
 * <p>The pattern in every command method is identical and deliberate:
 * <ol>
 *   <li>load or create the aggregate,</li>
 *   <li>invoke a domain behaviour (which enforces invariants + records events),</li>
 *   <li>{@code save} the aggregate and {@code append} its drained events to the
 *       outbox — atomically, because the method is {@code @Transactional}.</li>
 * </ol>
 * If the transaction rolls back, neither the state nor the events are committed.
 */
@Service
@Transactional
public class MeetingApplicationService
        implements ManageMeetingUseCase, ManageParticipantsUseCase, QueryMeetingUseCase {

    private final MeetingRepository meetings;
    private final DomainEventOutbox outbox;
    private final Clock clock;

    public MeetingApplicationService(MeetingRepository meetings, DomainEventOutbox outbox, Clock clock) {
        this.meetings = meetings;
        this.outbox = outbox;
        this.clock = clock;
    }

    // ── ManageMeetingUseCase ────────────────────────────────────────────────

    @Override
    public Meeting createMeeting(Commands.CreateMeeting command) {
        Meeting meeting = Meeting.schedule(
                command.title(), command.externalRef(), command.scheduledAt(), now());
        return persist(meeting);
    }

    @Override
    public Meeting startMeeting(String meetingId) {
        Meeting meeting = load(meetingId);
        meeting.start(now());
        return persist(meeting);
    }

    @Override
    public Meeting endMeeting(String meetingId) {
        Meeting meeting = load(meetingId);
        meeting.end(now());
        return persist(meeting);
    }

    // ── ManageParticipantsUseCase ───────────────────────────────────────────

    @Override
    public Meeting participantJoined(Commands.ParticipantJoin command) {
        Meeting meeting = load(command.meetingId());
        meeting.participantJoined(
                command.displayName(), command.platformUserId(), command.cameraOn(), now());
        return persist(meeting);
    }

    @Override
    public Meeting participantLeft(Commands.ParticipantLeave command) {
        Meeting meeting = load(command.meetingId());
        meeting.participantLeft(ParticipantId.of(command.participantId()), now());
        return persist(meeting);
    }

    @Override
    public Meeting setScreenShare(Commands.SetScreenShare command) {
        Meeting meeting = load(command.meetingId());
        meeting.participantScreenShare(
                ParticipantId.of(command.participantId()), command.sharing(), now());
        return persist(meeting);
    }

    // ── QueryMeetingUseCase ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Meeting getMeeting(String meetingId) {
        return load(meetingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Meeting> listMeetings() {
        return meetings.findAll();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Meeting load(String meetingId) {
        return meetings.findById(MeetingId.of(meetingId))
                .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    }

    /** Save aggregate + drain and enqueue its events in the same transaction. */
    private Meeting persist(Meeting meeting) {
        List<DomainEvent> events = meeting.pullDomainEvents();
        Meeting saved = meetings.save(meeting);
        outbox.append(events);
        return saved;
    }

    private Instant now() {
        return clock.instant();
    }
}
