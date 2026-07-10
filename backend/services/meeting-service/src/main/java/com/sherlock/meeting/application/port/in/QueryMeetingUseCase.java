package com.sherlock.meeting.application.port.in;

import com.sherlock.meeting.domain.model.Meeting;

import java.util.List;

/** Inbound port: read-side queries. Separated from command use cases (CQRS-lite). */
public interface QueryMeetingUseCase {

    Meeting getMeeting(String meetingId);

    List<Meeting> listMeetings();
}
