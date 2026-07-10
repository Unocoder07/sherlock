package com.sherlock.meeting.domain.model;

/** Raised when an operation is attempted from an incompatible meeting state. */
public class IllegalMeetingStateException extends RuntimeException {
    public IllegalMeetingStateException(String message) {
        super(message);
    }
}
