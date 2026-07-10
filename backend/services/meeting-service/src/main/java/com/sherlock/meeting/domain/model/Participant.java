package com.sherlock.meeting.domain.model;

import java.time.Instant;

/**
 * A participant who actually joined a meeting. This is an entity WITHIN the
 * {@link Meeting} aggregate — it is only ever created/mutated through the
 * aggregate root, never independently, so invariants stay centralized.
 *
 * <p>Note the deliberate distrust of {@code displayName}/{@code platformUserId}:
 * they are metadata (the WEAKEST signal in Sherlock) and may be generic
 * ("iPhone", "Guest"). Identity is decided elsewhere from biometrics; this
 * class only records the roster fact.
 */
public class Participant {

    private final ParticipantId id;
    private final String displayName;      // low-trust metadata, may be generic/null
    private final String platformUserId;   // low-trust metadata
    private final Instant joinedAt;
    private Instant leftAt;                 // null while present
    private boolean cameraOn;
    private boolean screenSharing;

    Participant(ParticipantId id, String displayName, String platformUserId,
                boolean cameraOn, Instant joinedAt) {
        if (id == null) {
            throw new IllegalArgumentException("Participant id must not be null");
        }
        if (joinedAt == null) {
            throw new IllegalArgumentException("joinedAt must not be null");
        }
        this.id = id;
        this.displayName = displayName;
        this.platformUserId = platformUserId;
        this.cameraOn = cameraOn;
        this.joinedAt = joinedAt;
    }

    /** Rehydrate an existing participant from persistence (bypasses "join" semantics). */
    public static Participant rehydrate(ParticipantId id, String displayName, String platformUserId,
                                        Instant joinedAt, Instant leftAt,
                                        boolean cameraOn, boolean screenSharing) {
        Participant p = new Participant(id, displayName, platformUserId, cameraOn, joinedAt);
        p.leftAt = leftAt;
        p.screenSharing = screenSharing;
        return p;
    }

    void markLeft(Instant when) {
        if (this.leftAt == null) {
            this.leftAt = when;
            this.screenSharing = false;
            this.cameraOn = false;
        }
    }

    void setScreenSharing(boolean sharing) {
        this.screenSharing = sharing;
    }

    void setCameraOn(boolean on) {
        this.cameraOn = on;
    }

    public boolean isPresent() {
        return leftAt == null;
    }

    public ParticipantId id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String platformUserId() {
        return platformUserId;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant leftAt() {
        return leftAt;
    }

    public boolean isCameraOn() {
        return cameraOn;
    }

    public boolean isScreenSharing() {
        return screenSharing;
    }
}
