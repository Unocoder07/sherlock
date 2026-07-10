package com.sherlock.meeting.domain.model;

import com.sherlock.meeting.domain.event.DomainEvent;
import com.sherlock.meeting.domain.event.MeetingEvents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Aggregate root for a meeting and its participant roster.
 *
 * <p>All mutations go through this class so invariants (legal state transitions,
 * no duplicate joins, no acting on an ended meeting) live in exactly one place.
 * State changes accumulate {@link DomainEvent}s in {@link #domainEvents}; the
 * application layer drains them via {@link #pullDomainEvents()} and persists them
 * to the outbox in the SAME transaction as the state change.
 */
public class Meeting {

    private final MeetingId id;
    private final String title;
    private final String externalRef;
    private final Instant scheduledAt;
    private MeetingState state;
    private Instant startedAt;
    private Instant endedAt;

    private final List<Participant> participants = new ArrayList<>();
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Meeting(MeetingId id, String title, String externalRef,
                    Instant scheduledAt, MeetingState state) {
        this.id = id;
        this.title = title;
        this.externalRef = externalRef;
        this.scheduledAt = scheduledAt;
        this.state = state;
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    /** Create a brand-new scheduled meeting and raise {@code MeetingCreated}. */
    public static Meeting schedule(String title, String externalRef, Instant scheduledAt, Instant now) {
        MeetingId id = MeetingId.newId();
        Meeting m = new Meeting(id, title, externalRef, scheduledAt, MeetingState.SCHEDULED);
        m.record(new MeetingEvents.MeetingCreated(
                id.asString(), title, externalRef, scheduledAt, now));
        return m;
    }

    /** Rehydrate from persistence WITHOUT raising events. */
    public static Meeting rehydrate(MeetingId id, String title, String externalRef, Instant scheduledAt,
                                    MeetingState state, Instant startedAt, Instant endedAt,
                                    List<Participant> existing) {
        Meeting m = new Meeting(id, title, externalRef, scheduledAt, state);
        m.startedAt = startedAt;
        m.endedAt = endedAt;
        if (existing != null) {
            m.participants.addAll(existing);
        }
        return m;
    }

    // ── Behaviour (state transitions) ────────────────────────────────────────

    /** Begin live monitoring. Idempotent if already LIVE. */
    public void start(Instant now) {
        if (state == MeetingState.LIVE) {
            return;
        }
        transitionTo(MeetingState.LIVE, now);
        this.startedAt = now;
    }

    /** End the meeting. Idempotent if already ENDED. */
    public void end(Instant now) {
        if (state == MeetingState.ENDED) {
            return;
        }
        transitionTo(MeetingState.ENDED, now);
        this.endedAt = now;
        // Anyone still present is implicitly gone when the meeting ends.
        for (Participant p : participants) {
            if (p.isPresent()) {
                p.markLeft(now);
                record(new MeetingEvents.ParticipantLeft(id.asString(), p.id().asString(), now));
            }
        }
    }

    private void transitionTo(MeetingState target, Instant now) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalMeetingStateException(
                    "Cannot transition meeting %s from %s to %s".formatted(id.asString(), state, target));
        }
        this.state = target;
        record(new MeetingEvents.MeetingStateChanged(id.asString(), target.name(), now));
    }

    // ── Behaviour (roster) ───────────────────────────────────────────────────

    public Participant participantJoined(String displayName, String platformUserId,
                                         boolean cameraOn, Instant now) {
        requireNotEnded();
        ParticipantId pid = ParticipantId.newId();
        Participant participant = new Participant(pid, displayName, platformUserId, cameraOn, now);
        participants.add(participant);
        record(new MeetingEvents.ParticipantJoined(
                id.asString(), pid.asString(), displayName, platformUserId, cameraOn, now));
        return participant;
    }

    public void participantLeft(ParticipantId participantId, Instant now) {
        Participant p = requireParticipant(participantId);
        if (p.isPresent()) {
            p.markLeft(now);
            record(new MeetingEvents.ParticipantLeft(id.asString(), participantId.asString(), now));
        }
    }

    public void participantScreenShare(ParticipantId participantId, boolean sharing, Instant now) {
        requireNotEnded();
        Participant p = requireParticipant(participantId);
        p.setScreenSharing(sharing);
        record(new MeetingEvents.ScreenShareChanged(
                id.asString(), participantId.asString(), sharing, now));
    }

    private void requireNotEnded() {
        if (state == MeetingState.ENDED) {
            throw new IllegalMeetingStateException(
                    "Meeting %s has ended; no further roster changes allowed".formatted(id.asString()));
        }
    }

    private Participant requireParticipant(ParticipantId participantId) {
        return findParticipant(participantId).orElseThrow(() ->
                new ParticipantNotFoundException(
                        "Participant %s not found in meeting %s"
                                .formatted(participantId.asString(), id.asString())));
    }

    public Optional<Participant> findParticipant(ParticipantId participantId) {
        return participants.stream().filter(p -> p.id().equals(participantId)).findFirst();
    }

    // ── Domain event plumbing ────────────────────────────────────────────────

    private void record(DomainEvent event) {
        domainEvents.add(event);
    }

    /** Drain accumulated events (called once by the application layer per unit of work). */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> drained = List.copyOf(domainEvents);
        domainEvents.clear();
        return drained;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public MeetingId id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String externalRef() {
        return externalRef;
    }

    public Instant scheduledAt() {
        return scheduledAt;
    }

    public MeetingState state() {
        return state;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public List<Participant> participants() {
        return Collections.unmodifiableList(participants);
    }
}
