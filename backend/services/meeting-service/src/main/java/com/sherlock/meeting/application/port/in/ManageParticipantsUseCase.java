package com.sherlock.meeting.application.port.in;

import com.sherlock.meeting.application.dto.Commands;
import com.sherlock.meeting.domain.model.Meeting;

/** Inbound port: roster operations on a meeting. */
public interface ManageParticipantsUseCase {

    Meeting participantJoined(Commands.ParticipantJoin command);

    Meeting participantLeft(Commands.ParticipantLeave command);

    Meeting setScreenShare(Commands.SetScreenShare command);
}
