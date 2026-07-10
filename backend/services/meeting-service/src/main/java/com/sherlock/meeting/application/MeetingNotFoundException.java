package com.sherlock.meeting.application;

/** Raised by use cases when a referenced meeting does not exist. */
public class MeetingNotFoundException extends RuntimeException {
    public MeetingNotFoundException(String meetingId) {
        super("Meeting not found: " + meetingId);
    }
}
