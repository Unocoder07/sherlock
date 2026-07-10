package com.sherlock.meeting.domain.model;

import com.sherlock.meeting.domain.event.DomainEvent;
import com.sherlock.meeting.domain.event.MeetingEvents;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests — no Spring, no DB. These lock in the aggregate invariants
 * that the whole system's correctness depends on.
 */
class MeetingTest {

    private static final Instant T0 = Instant.parse("2026-07-08T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-08T10:05:00Z");

    @Test
    void schedule_raisesMeetingCreated_inScheduledState() {
        Meeting m = Meeting.schedule("Backend interview", "ext-1", T0, T0);

        assertThat(m.state()).isEqualTo(MeetingState.SCHEDULED);
        assertThat(m.pullDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingEvents.MeetingCreated.class);
    }

    @Test
    void start_thenEnd_followsLegalTransitions_andEmitsEvents() {
        Meeting m = Meeting.schedule("t", null, T0, T0);
        m.pullDomainEvents(); // discard creation event

        m.start(T0);
        assertThat(m.state()).isEqualTo(MeetingState.LIVE);

        m.end(T1);
        assertThat(m.state()).isEqualTo(MeetingState.ENDED);

        List<DomainEvent> events = m.pullDomainEvents();
        assertThat(events).hasAtLeastOneElementOfType(MeetingEvents.MeetingStateChanged.class);
    }

    @Test
    void cannotStartAfterEnded() {
        Meeting m = Meeting.schedule("t", null, T0, T0);
        m.end(T0);

        assertThatThrownBy(() -> m.start(T1))
                .isInstanceOf(IllegalMeetingStateException.class);
    }

    @Test
    void participantJoin_addsToRoster_andRaisesEvent() {
        Meeting m = Meeting.schedule("t", null, T0, T0);
        m.pullDomainEvents();

        Participant p = m.participantJoined("iPhone", "plat-9", false, T0);

        assertThat(m.participants()).containsExactly(p);
        assertThat(p.isPresent()).isTrue();
        assertThat(m.pullDomainEvents())
                .singleElement()
                .isInstanceOf(MeetingEvents.ParticipantJoined.class);
    }

    @Test
    void endingMeeting_marksPresentParticipantsLeft() {
        Meeting m = Meeting.schedule("t", null, T0, T0);
        Participant p = m.participantJoined("Guest", null, true, T0);
        m.pullDomainEvents();

        m.end(T1);

        assertThat(p.isPresent()).isFalse();
        assertThat(p.leftAt()).isEqualTo(T1);
        assertThat(m.pullDomainEvents()).anyMatch(e -> e instanceof MeetingEvents.ParticipantLeft);
    }

    @Test
    void cannotAddParticipantAfterEnded() {
        Meeting m = Meeting.schedule("t", null, T0, T0);
        m.end(T0);

        assertThatThrownBy(() -> m.participantJoined("x", null, false, T1))
                .isInstanceOf(IllegalMeetingStateException.class);
    }
}
