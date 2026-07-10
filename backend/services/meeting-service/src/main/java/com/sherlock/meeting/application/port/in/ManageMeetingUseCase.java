package com.sherlock.meeting.application.port.in;

import com.sherlock.meeting.application.dto.Commands;
import com.sherlock.meeting.domain.model.Meeting;

/**
 * Inbound port: the meeting lifecycle use cases exposed to driving adapters
 * (REST today, possibly a message consumer later). Adapters depend on this
 * interface, never on the concrete service.
 */
public interface ManageMeetingUseCase {

    Meeting createMeeting(Commands.CreateMeeting command);

    /** Begin live monitoring (SCHEDULED -> LIVE). */
    Meeting startMeeting(String meetingId);

    /** End the meeting (-> ENDED). */
    Meeting endMeeting(String meetingId);
}
