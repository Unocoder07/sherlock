-- Notification Service audit trail (doc 02 §8). One row per fired alert. This is
-- the durable record behind the REST query; the same alert is republished on the
-- notifications topic for live push (the CRITICAL dashboard banner).

CREATE TABLE IF NOT EXISTS notification.alert (
    notification_id TEXT PRIMARY KEY,                -- UUID assigned on fire
    meeting_id      TEXT        NOT NULL,
    participant_id  TEXT        NOT NULL,
    severity        TEXT        NOT NULL,            -- INFO | WARNING | CRITICAL
    rule            TEXT        NOT NULL,
    title           TEXT        NOT NULL,
    message         TEXT        NOT NULL DEFAULT '',
    state           TEXT        NOT NULL DEFAULT '',
    occurred_at_ms  BIGINT      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alert_meeting
    ON notification.alert (meeting_id, occurred_at_ms);
