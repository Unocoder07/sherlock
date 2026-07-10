package com.sherlock.meeting.domain.model;

/** Raised when a referenced participant does not exist in the meeting. */
public class ParticipantNotFoundException extends RuntimeException {
    public ParticipantNotFoundException(String message) {
        super(message);
    }
}
