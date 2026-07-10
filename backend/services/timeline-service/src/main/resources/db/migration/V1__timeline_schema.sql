-- Timeline Service durable history (doc 02 §6). Append-only: one row per notable
-- moment (state transition or score inflection). The same row is republished on
-- timeline.events for live push; this table backs the REST timeline query and
-- survives restarts (the consumer replays the topic to rebuild in-memory state).

CREATE TABLE IF NOT EXISTS timeline.entry (
    entry_id       TEXT PRIMARY KEY,                 -- UUID assigned on append
    meeting_id     TEXT        NOT NULL,
    participant_id TEXT        NOT NULL,
    kind           TEXT        NOT NULL,             -- STATE_TRANSITION | SCORE_INFLECTION
    from_state     TEXT        NOT NULL DEFAULT '',
    to_state       TEXT        NOT NULL DEFAULT '',
    score          DOUBLE PRECISION NOT NULL,
    headline       TEXT        NOT NULL DEFAULT '',
    detail         TEXT        NOT NULL DEFAULT '',
    occurred_at_ms BIGINT      NOT NULL              -- event time (ordering key)
);

CREATE INDEX IF NOT EXISTS idx_timeline_entry_meeting
    ON timeline.entry (meeting_id, occurred_at_ms);
