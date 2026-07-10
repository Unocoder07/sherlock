-- Meeting Service owns these tables. Schema is created here too (idempotent) so the
-- migration is self-contained for tests (Testcontainers) as well as the shared dev DB.

CREATE SCHEMA IF NOT EXISTS meeting;

-- ── meetings ────────────────────────────────────────────────────────────────
CREATE TABLE meeting.meetings (
    id            UUID PRIMARY KEY,
    title         TEXT,
    external_ref  TEXT,
    state         TEXT        NOT NULL,     -- SCHEDULED | LIVE | ENDED
    scheduled_at  TIMESTAMPTZ,
    started_at    TIMESTAMPTZ,
    ended_at      TIMESTAMPTZ,
    version       BIGINT      NOT NULL DEFAULT 0,   -- optimistic locking
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── participants (child of meetings) ─────────────────────────────────────────
CREATE TABLE meeting.participants (
    id                UUID PRIMARY KEY,
    meeting_id        UUID        NOT NULL REFERENCES meeting.meetings(id) ON DELETE CASCADE,
    display_name      TEXT,                          -- low-trust metadata (may be generic)
    platform_user_id  TEXT,                          -- low-trust metadata
    joined_at         TIMESTAMPTZ NOT NULL,
    left_at           TIMESTAMPTZ,
    camera_on         BOOLEAN     NOT NULL DEFAULT FALSE,
    screen_sharing    BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_participants_meeting ON meeting.participants(meeting_id);

-- ── transactional outbox ──────────────────────────────────────────────────────
-- Written in the SAME transaction as the aggregate change; a relay publishes rows
-- to Kafka and marks them SENT. Guarantees state + event commit atomically.
CREATE TABLE meeting.outbox_events (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID        NOT NULL,     -- meeting id (also the Kafka partition key)
    event_type    TEXT        NOT NULL,     -- fully-qualified payload type
    topic         TEXT        NOT NULL,
    msg_key       TEXT        NOT NULL,     -- Kafka key
    payload       BYTEA       NOT NULL,     -- serialized EventEnvelope (protobuf)
    status        TEXT        NOT NULL DEFAULT 'PENDING',   -- PENDING | SENT
    attempts      INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at       TIMESTAMPTZ
);
-- Relay polls this partial index; it stays tiny because SENT rows drop out of it.
CREATE INDEX idx_outbox_pending ON meeting.outbox_events(created_at)
    WHERE status = 'PENDING';
