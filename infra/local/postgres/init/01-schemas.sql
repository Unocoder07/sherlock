-- ─────────────────────────────────────────────────────────────────────────────
-- Sherlock — bounded-context schemas.
-- Each service OWNS its schema and manages its own tables via Flyway migrations
-- (added per service in later milestones). This init only creates the namespaces
-- and a dedicated app role, so the DB is ready before any service starts.
-- Runs once, automatically, on first Postgres startup.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS meeting;       -- Meeting Service (meetings, participants, optional reference)
CREATE SCHEMA IF NOT EXISTS anchor;        -- Identity Anchor Service (self-built reference snapshots)
CREATE SCHEMA IF NOT EXISTS evidence;      -- Evidence Fusion Engine (append-only evidence audit)
CREATE SCHEMA IF NOT EXISTS confidence;    -- Confidence Engine (belief snapshots, verdicts)
CREATE SCHEMA IF NOT EXISTS timeline;      -- Timeline Service (immutable timeline)
CREATE SCHEMA IF NOT EXISTS notification;  -- Notification Service (alert audit)

-- Useful extensions (UUIDs, trigram search for future admin queries).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

COMMENT ON SCHEMA meeting     IS 'Meeting Service: lifecycle + participants roster';
COMMENT ON SCHEMA anchor      IS 'Identity Anchor Service: self-built identity reference';
COMMENT ON SCHEMA evidence    IS 'Evidence Fusion Engine: normalized evidence audit trail';
COMMENT ON SCHEMA confidence  IS 'Confidence Engine: stateful belief snapshots + verdicts';
COMMENT ON SCHEMA timeline    IS 'Timeline Service: immutable ordered history';
COMMENT ON SCHEMA notification IS 'Notification Service: alert audit trail';
