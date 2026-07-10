package com.sherlock.meeting.domain.model;

/**
 * Lifecycle of a meeting. Transitions are enforced by the {@link Meeting} aggregate,
 * not by callers — this enum only defines the legal states.
 */
public enum MeetingState {
    SCHEDULED,
    LIVE,
    ENDED;

    /** Whether a transition from this state to {@code target} is allowed. */
    public boolean canTransitionTo(MeetingState target) {
        return switch (this) {
            case SCHEDULED -> target == LIVE || target == ENDED;
            case LIVE -> target == ENDED;
            case ENDED -> false; // terminal
        };
    }
}
