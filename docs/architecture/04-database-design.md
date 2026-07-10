# 04 — Database Design

## 1. Storage Strategy — Polyglot Persistence

| Store | Holds | Why |
|---|---|---|
| **PostgreSQL** | Durable domain state: meetings, participants, enrolments, evidence audit, timeline, verdict snapshots, notifications | ACID, rich queries, relational integrity, audit trail |
| **Redis** | Hot per-participant belief state, sliding-window accumulators, idempotency seen-sets, WS session/subscription fan-out, caches | Sub-ms latency, TTL, atomic ops for the stateful scoring path |
| **Object store (S3 / MinIO)** | Frame clips, face/voice embeddings, reference biometrics | Large binaries kept out of Postgres/Kafka; referenced by URI |

> **Data ownership:** each service owns its tables. No cross-service table writes. Services integrate via events, not shared schemas — this is what keeps them independently deployable.

## 2. PostgreSQL Schema (logical)

Schemas are namespaced per bounded context. Times are `timestamptz` (UTC). IDs are UUIDs.

### 2.1 `meeting` context

```sql
CREATE TABLE meeting.meetings (
    id                UUID PRIMARY KEY,
    external_ref      TEXT,                 -- platform meeting id (Zoom/Meet), nullable
    title             TEXT,
    state             TEXT NOT NULL,        -- SCHEDULED | LIVE | ENDED
    scheduled_at      TIMESTAMPTZ,
    started_at        TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- OPTIONAL. Only populated in reference-anchored mode, and ONLY from a
-- reference captured LIVE at join (ID-verification), never a pre-upload.
-- In the default zero-knowledge mode this table is empty; identity comes
-- from the self-built anchor below.
CREATE TABLE meeting.expected_candidates (           -- who SHOULD be in the interview (optional)
    id                UUID PRIMARY KEY,
    meeting_id        UUID NOT NULL REFERENCES meeting.meetings(id),
    display_name      TEXT,                 -- metadata only; may be a generic name
    external_ref      TEXT,                 -- ATS/candidate id
    face_ref_uri      TEXT,                 -- OPTIONAL live-captured reference face embedding
    voice_ref_uri     TEXT,                 -- OPTIONAL live-captured reference voice embedding
    reference_source  TEXT,                 -- NULL | LIVE_ID_CAPTURE | PRIOR_SCREENING
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (meeting_id, external_ref)
);

CREATE TABLE meeting.participants (                  -- who ACTUALLY joined
    id                UUID PRIMARY KEY,
    meeting_id        UUID NOT NULL REFERENCES meeting.meetings(id),
    display_name      TEXT,                 -- may be generic: "iPhone", "Guest"
    platform_user_id  TEXT,                 -- metadata, LOW trust
    joined_at         TIMESTAMPTZ NOT NULL,
    left_at           TIMESTAMPTZ,
    camera_on         BOOLEAN,
    is_screen_sharing BOOLEAN DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_participants_meeting ON meeting.participants(meeting_id);
```

### 2.1a `anchor` context (the self-built reference — Identity Anchor Service)

```sql
CREATE TABLE anchor.identity_anchors (               -- one evolving anchor per meeting
    meeting_id           UUID PRIMARY KEY,
    anchored_participant UUID,                 -- the participant the system anchored on
    lifecycle_state      TEXT NOT NULL,        -- OBSERVING | ANCHORING | LOCKED | BROKEN
    mode                 TEXT NOT NULL,        -- SELF | REFERENCE_SEEDED
    face_centroid_uri    TEXT,                 -- object-store ref: online face-embedding centroid
    voice_centroid_uri   TEXT,                 -- object-store ref: online voice-embedding centroid
    av_binding_score     REAL,                 -- 0..1 confidence the visible face is the speaker
    stability            REAL,                 -- 0..1 cluster tightness / dominance sustained
    locked_at            TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE anchor.anchor_events (                  -- audit of anchor lifecycle transitions
    id                UUID PRIMARY KEY,
    meeting_id        UUID NOT NULL,
    event_type        TEXT NOT NULL,           -- ANCHOR_FORMING | ANCHOR_LOCKED | ANCHOR_DRIFT | ANCHOR_BROKEN
    detail            JSONB,
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_anchor_events_meeting_time ON anchor.anchor_events(meeting_id, occurred_at);
```

> Embedding centroids are stored as object-store references (vectors), not inline. The working centroid lives in Redis; this table is the durable snapshot for recovery, mirroring the Confidence Engine pattern.

### 2.2 `evidence` context (audit trail — append only)

```sql
CREATE TABLE evidence.evidence_records (
    id                UUID PRIMARY KEY,
    meeting_id        UUID NOT NULL,
    participant_id    UUID NOT NULL,
    evidence_type     TEXT NOT NULL,        -- FACE_MATCH | VOICE_MATCH | SPEAKING | SCREEN_SHARE | METADATA_NAME ...
    source            TEXT NOT NULL,        -- VIDEO | AUDIO | MEETING
    raw_value         REAL,
    weight            REAL NOT NULL,
    reliability       REAL NOT NULL,        -- 0..1
    polarity          SMALLINT NOT NULL,    -- +1 / -1
    occurred_at       TIMESTAMPTZ NOT NULL,
    ingested_at       TIMESTAMPTZ NOT NULL DEFAULT now()
) PARTITION BY RANGE (occurred_at);        -- monthly partitions; high volume
CREATE INDEX idx_evidence_meeting_time ON evidence.evidence_records(meeting_id, occurred_at);
```

### 2.3 `confidence` context (durable snapshots for recovery)

```sql
CREATE TABLE confidence.participant_state (          -- latest snapshot per participant
    meeting_id        UUID NOT NULL,
    participant_id    UUID NOT NULL,
    score             REAL NOT NULL,
    state             TEXT NOT NULL,        -- see state machine
    accumulator       JSONB NOT NULL,       -- serialized weighted evidence accumulator
    last_evidence_off BIGINT,               -- last applied evidence.events offset (recovery)
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (meeting_id, participant_id)
);

CREATE TABLE confidence.verdicts (                   -- the "current candidate" per meeting, versioned
    id                UUID PRIMARY KEY,
    meeting_id        UUID NOT NULL,
    candidate_participant_id UUID,
    score             REAL NOT NULL,
    state             TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_verdicts_meeting_time ON confidence.verdicts(meeting_id, created_at DESC);
```

### 2.4 `timeline` context

```sql
CREATE TABLE timeline.timeline_entries (
    id                UUID PRIMARY KEY,
    meeting_id        UUID NOT NULL,
    participant_id    UUID,
    entry_type        TEXT NOT NULL,        -- STATE_CHANGE | EVIDENCE | SCORE_INFLECTION | ALERT
    headline          TEXT NOT NULL,        -- rendered by Explanation Engine
    detail            JSONB,
    score_at_entry    REAL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_timeline_meeting_time ON timeline.timeline_entries(meeting_id, occurred_at);
```

### 2.5 `notification` context

```sql
CREATE TABLE notification.notifications (
    id                UUID PRIMARY KEY,
    meeting_id        UUID NOT NULL,
    severity          TEXT NOT NULL,        -- INFO | WARN | CRITICAL
    rule             TEXT NOT NULL,         -- e.g. PROXY_SUSPECTED
    channel          TEXT NOT NULL,         -- EMAIL | WEBHOOK | IN_APP
    payload          JSONB,
    delivered        BOOLEAN DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## 3. Entity-Relationship Overview

```
meetings 1───* expected_candidates
meetings 1───* participants
participants 1───* evidence_records        (by participant_id, logical FK, cross-context)
participants 1───1 participant_state
meetings 1───* verdicts                    (time-series of the current candidate)
meetings 1───* timeline_entries
meetings 1───* notifications
```

> Cross-context references (e.g., `evidence_records.participant_id`) are **logical**, not enforced FKs, because those tables are owned by different services/databases in the target topology. Referential integrity within a context is enforced; across contexts it's eventual.

## 4. Redis Data Model (hot state)

| Key pattern | Type | Purpose | TTL |
|---|---|---|---|
| `conf:{meetingId}:{participantId}` | Hash | Live accumulator + score + state | meeting-scoped |
| `conf:{meetingId}:winner` | String | Current best candidate participantId | meeting-scoped |
| `win:{meetingId}:{participantId}:speaking` | Sorted set | Sliding window of speaking events (event-time scored) | short |
| `seen:{meetingId}:{participantId}` | Set / HyperLogLog | Idempotency seen-set of `event_id` | short |
| `ws:subs:{meetingId}` | Set | Connected WS session ids for fan-out | session |

The accumulator in Redis is the working copy; PostgreSQL `participant_state` is the durable checkpoint written on every state transition and periodically (e.g., every N updates or T seconds) for crash recovery.

## 5. Partitioning, Retention, Scaling

- **`evidence_records`**: highest-volume table → **range-partitioned by month**; old partitions detached/archived to object store. Kept for audit/explainability.
- **`timeline_entries` / `verdicts`**: partition by month if volume warrants; indexed by `(meeting_id, time)`.
- **Read replicas** for dashboard queries (timeline, history) to keep them off the write path.
- **Connection pooling** via PgBouncer in production.

## 6. Consistency Model

- **Within a meeting's control-plane data** (meeting/participant state): strong consistency in PostgreSQL.
- **Between the live verdict (Redis) and its durable snapshot (PostgreSQL):** eventual, bounded by the snapshot interval; on recovery we replay `evidence.events` from `last_evidence_off` to rebuild exact state.
- **Verdict history (`verdicts`)** is an append-only audit; the "current" verdict is `ORDER BY created_at DESC LIMIT 1`.

---

*Previous: [03 — Event-Driven Design](./03-event-driven-design.md) · Next: [05 — Confidence Engine](./05-confidence-engine.md)*
