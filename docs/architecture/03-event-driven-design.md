# 03 — Event-Driven Design (Kafka Topics, Schemas, Guarantees)

## 1. Topic Catalogue

All topics are **keyed by `meetingId`** so that every event for one meeting lands on the same partition and is processed in order. `participantId` lives in the payload.

| Topic | Producer(s) | Consumer(s) | Partitions (initial) | Retention | Notes |
|---|---|---|---|---|---|
| `meeting.events` | Meeting Service | Evidence Fusion | 12 | 30d | Lifecycle + metadata |
| `media.frames.meta` | Ingestion adapter | Video Svc, Audio Svc | 24 | 6h | High volume, short retention; refs only |
| `video.signals` | Video Processing | Identity Anchor, Evidence Fusion | 24 | 7d | Face embeddings + lip-activity |
| `audio.signals` | Audio Processing | Identity Anchor, Evidence Fusion | 24 | 7d | Voice embeddings + VAD/dominance |
| `identity.anchor` | Identity Anchor Svc | Evidence Fusion, Timeline, Notification | 12 | 30d | Anchor lifecycle + consistency signals (self-built reference) |
| `evidence.events` | Evidence Fusion | Confidence Engine, Timeline | 12 | 30d | Normalized evidence |
| `confidence.updates` | Confidence Engine | Timeline, Explanation, Notification, WS GW | 12 | 30d | Verdicts + contributions |
| `timeline.events` | Timeline Service | WS Gateway | 12 | 30d | Live timeline push |
| `notifications` | Notification Service | (audit sink) | 6 | 90d | Alert audit trail |
| `*.DLT` (dead-letter) | all consumers | ops tooling | 3 each | 30d | Poison-message quarantine |

> **Why partition count > current need?** Kafka partitions cannot be reduced later and cap consumer parallelism. We over-provision the hot topics (media/signals at 24) so we can scale AI workers without re-partitioning. `meetingId` keying guarantees ordering per meeting regardless of partition count.

## 2. Event Envelope (common schema)

Every event shares an envelope. Payload is schema-versioned and validated via the **Schema Registry** (Avro/Protobuf). We recommend **Protobuf** for compactness on the media/signal firehose and cross-language (Java + Python) codegen.

```
message EventEnvelope {
  string        event_id      = 1;  // UUID, idempotency key
  string        event_type    = 2;  // e.g. "video.face_match"
  int32         schema_version = 3;
  string        meeting_id    = 4;  // partition key
  string        participant_id = 5; // optional for meeting-level events
  int64         occurred_at_ms = 6; // event time (source clock)
  int64         emitted_at_ms  = 7; // producer wall clock
  string        producer      = 8;  // service name + version
  string        trace_id      = 9;  // W3C traceparent for distributed tracing
  bytes         payload       = 10; // typed per event_type
}
```

**Two timestamps on purpose.** `occurred_at_ms` (event time) drives temporal fusion and decay; `emitted_at_ms` (processing time) drives lag/latency monitoring. Out-of-order arrival is handled by ordering on event time within a bounded window.

## 3. Representative Payloads

```
// video.signals — FACE_MATCH
message FaceMatchSignal {
  float  match_score      = 1;  // 0..1 cosine vs enrolled reference
  float  detection_conf   = 2;  // detector confidence
  float  quality          = 3;  // blur/pose/illumination composite
  int32  face_count       = 4;  // >1 flags MULTIPLE_FACES
  string embedding_ref    = 5;  // object-store ref (audit / re-scoring)
}

// audio.signals — VOICE_MATCH
message VoiceMatchSignal {
  float  match_score      = 1;  // vs enrolled voice embedding
  float  speaking_ratio   = 2;  // fraction of window this participant spoke
  bool   multiple_speakers = 3;
  float  snr              = 4;  // signal-to-noise, reliability input
}

// evidence.events — normalized
message EvidenceRecord {
  string  evidence_type   = 1;  // FACE_MATCH | VOICE_MATCH | SPEAKING | SCREEN_SHARE | METADATA_NAME ...
  string  source          = 2;  // VIDEO | AUDIO | MEETING
  float   raw_value       = 3;  // e.g. match score
  float   weight          = 4;  // base weight for this evidence type
  float   reliability     = 5;  // 0..1 confidence in this observation
  int32   polarity        = 6;  // +1 supports identity, -1 contradicts
  int64   occurred_at_ms  = 7;
}

// confidence.updates — verdict
message ConfidenceUpdate {
  string  candidate_id    = 1;
  float   score           = 2;  // 0..1
  string  state           = 3;  // INITIALIZING | UNCERTAIN | IDENTIFIED | PROXY_SUSPECTED ...
  repeated Contribution contributions = 4; // per evidence-type breakdown for explanation
  string  previous_state  = 5;
  int64   ts              = 6;
}
```

## 4. Delivery Guarantees & Idempotency

- **At-least-once delivery** on all topics. Consumers **must be idempotent**, keyed on `event_id`.
- **Confidence Engine idempotency:** applying the same evidence twice must not double-count. Implemented via a per-participant **seen-set / last-applied-offset** in Redis, and by making state updates a function of `(evidence, occurred_at)` rather than blind increments.
- **Outbox pattern** in every Java producer: domain state and the outgoing event are written in one PostgreSQL transaction; a relay publishes to Kafka. Prevents "state changed but event lost" and vice versa.
- **Exactly-once *effects*** are achieved at the application layer (idempotent consumers), not by relying on Kafka EOS across languages.

## 5. Ordering & Time

- Ordering is guaranteed **per partition = per meeting**. Cross-meeting ordering is irrelevant.
- The Confidence Engine buffers evidence in a small **event-time window** (e.g., 2s) and sorts by `occurred_at_ms` before applying, absorbing minor reordering from parallel AI workers.
- **Late events** (beyond the window) are still applied but flagged `late=true` and contribute with reduced weight, since a stale observation should not overturn a fresh belief.

## 6. Backpressure & Overload

- Kafka *is* the buffer. If AI workers fall behind, consumer lag grows; we alert and autoscale on lag (Kubernetes HPA on Kafka lag metric).
- The ingestion adapter applies **adaptive frame sampling**: under sustained lag it drops from, e.g., 5 fps to 2 fps for face and widens audio windows, preserving identity accuracy (which needs seconds, not milliseconds) while shedding load. Dropped-frame counts are emitted as metrics so degradation is observable, never silent.

## 7. Failure Handling

- **Retryable errors** (transient model load, object-store blip): in-consumer retry with exponential backoff, capped.
- **Poison messages** (unparseable, repeatedly failing): routed to `<topic>.DLT` with the failure reason; never block the partition.
- **Consumer crash:** Kafka rebalances partitions to healthy instances; Confidence Engine state for those partitions is rehydrated from the latest PostgreSQL snapshot + replay of `evidence.events` since the snapshot offset.

## 8. Why Kafka specifically (vs. RabbitMQ / SQS)

| Need | Kafka fit |
|---|---|
| Ordered per-meeting processing | Partition keying gives per-key ordering natively |
| Replay for audit / recovery / re-scoring | Log retention + offset seeking |
| High-throughput signal firehose | Designed for it; cheap sequential I/O |
| Multiple independent consumers of one stream | Consumer groups; each service reads at its own pace |
| Stateful stream recovery | Offset + snapshot pattern |

RabbitMQ is queue-centric (poor at replay/multi-consumer fan-out at this volume); SQS lacks strong ordering + replay ergonomics. Kafka is the right backbone here.

---

*Previous: [02 — Service Breakdown](./02-service-breakdown.md) · Next: [04 — Database Design](./04-database-design.md)*
