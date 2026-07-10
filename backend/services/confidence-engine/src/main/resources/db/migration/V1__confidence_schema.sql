-- Confidence Engine durable state (doc 05 §8). The working belief lives in
-- memory (hot copy in Redis); this table is the checkpoint written on every
-- state transition so a verdict survives restart / partition rebalance.

CREATE TABLE IF NOT EXISTS confidence.participant_state (
    id             TEXT PRIMARY KEY,                 -- meetingId:participantId
    meeting_id     TEXT        NOT NULL,
    participant_id TEXT        NOT NULL,
    state          TEXT        NOT NULL,
    score          DOUBLE PRECISION NOT NULL,
    separation     DOUBLE PRECISION NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_participant_state_meeting
    ON confidence.participant_state (meeting_id);
