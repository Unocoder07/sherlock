# 02 — Service Breakdown, Responsibilities & Low-Level Design

Each service is described with: **Responsibility**, **Owns / does NOT own**, **Inputs**, **Outputs**, **Internal LLD** (layers/key components), and **Tech**.

> **Single Responsibility rule:** if a service's description needs the word "and" more than once, it is probably two services. The list below is deliberately granular so each can scale and fail independently.

---

## 1. Meeting Service (Java / Spring Boot)

**Responsibility:** Own the meeting lifecycle and the roster of participants. The system's control-plane entry point for a session.

- **Owns:** `Meeting`, `Participant`, `MeetingSession` aggregates; meeting state (SCHEDULED, LIVE, ENDED); participant join/leave; **optionally** an externally-verified reference captured at join (ID-card + live face/voice) for reference-anchored mode. **No reference is required** — the default is zero-knowledge self-anchoring (see doc 05 §1.1).
- **Does NOT own:** any identity verdict, any AI processing, any scoring, the self-built identity anchor (that is the Identity Anchor Service).
- **Inputs:** REST commands (create meeting, start/stop monitoring, *optional* live ID-verification capture); platform webhooks or an ingestion adapter reporting join/leave/screen-share.
- **Outputs:** `meeting.events` (PARTICIPANT_JOINED, PARTICIPANT_LEFT, SCREEN_SHARE_STARTED/STOPPED, MEETING_STARTED/ENDED, METADATA_UPDATED).
- **LLD layers:** Controller → Application Service → Domain (aggregates + domain events) → Repository (JPA) → Kafka producer adapter. Outbox pattern for reliable event publish.

## 2. Video Processing Service (Python / FastAPI worker)

**Responsibility:** Turn video frames into **video signals**. Stateless per frame.

- **Owns:** face detection (MediaPipe), face embedding + matching (InsightFace), face-presence tracking (frame-to-frame association), quality scoring (blur, illumination, pose), liveness heuristics (optional/future).
- **Does NOT own:** the notion of "who the candidate is" — it only reports *what it sees*.
- **Inputs:** `media.frames.meta` (fetches frame bytes from object store by reference).
- **Outputs:** `video.signals` — per participant per window: `FACE_PRESENT`, `FACE_MATCH{score}` (vs. enrolled reference), `FACE_ABSENT` (camera off / occlusion), `MULTIPLE_FACES`, `FACE_CHANGED` (embedding shifted → possible person switch), `QUALITY{score}`.
- **LLD components:** ingestion consumer → frame decoder → detector pipeline (detect → align → embed → match) → signal builder → Kafka producer. Model loading via a singleton model registry; batched inference; GPU-aware worker pool.

## 3. Audio Processing Service (Python / FastAPI worker)

**Responsibility:** Turn audio chunks into **audio signals**. Stateless per chunk.

- **Owns:** voice activity detection (Silero VAD), speaker diarization + embedding (pyannote.audio), speaker-consistency matching against enrolled voice reference, dominance measurement (who talks, how much).
- **Does NOT own:** speech content / transcription (out of scope v1; could feed "answered N questions" later via an ASR add-on).
- **Inputs:** `media.frames.meta` (audio chunk references).
- **Outputs:** `audio.signals` — `SPEAKING{participant}`, `VOICE_MATCH{score}`, `VOICE_CHANGED`, `SILENCE`, `MULTIPLE_SPEAKERS`, `DOMINANCE{ratio}`.
- **LLD components:** consumer → audio decoder → VAD gate → diarization/embedding → matcher → dominance accumulator (short sliding window) → signal builder → producer.

## 3.1 Identity Anchor Service (Java / Spring Boot) — **STATEFUL**

**Responsibility:** Solve the cold-start problem — with **zero prior knowledge**, learn *who the candidate is* from the interview itself and maintain that self-built reference (the "anchor"). This is the service that lets the system "start with 0 info and gain candidate info as the interview proceeds."

- **Owns:** the per-meeting **Identity Anchor** = an online-updated centroid of the dominant participant's face embeddings + voice embeddings, plus the **audio-visual binding** (which visible face corresponds to the active voice, via lip-motion↔VAD correlation), plus anchor lifecycle (`OBSERVING → ANCHORING → LOCKED`), stability/confidence, and drift detection.
- **Two modes (same output contract):**
  - *Self-anchored (default):* builds the anchor purely from observed signals — no reference needed.
  - *Reference-anchored (optional):* seeds the anchor from an externally-verified reference captured live at join (from the Meeting Service). Strengthens the guarantee to true verification.
- **Does NOT own:** scoring/state (Confidence Engine) or per-signal normalization (Evidence Fusion).
- **Inputs:** `video.signals` (face embeddings + lip-activity), `audio.signals` (voice embeddings + VAD), optional reference from `meeting.events`.
- **Outputs:** `identity.anchor` events (ANCHOR_FORMING, ANCHOR_LOCKED, ANCHOR_DRIFT, ANCHOR_BROKEN) **and** derived consistency signals: `FACE_CONSISTENT_WITH_ANCHOR{score}`, `VOICE_CONSISTENT_WITH_ANCHOR{score}`, `AV_BINDING{score}`, `ANCHOR_MISMATCH` (→ proxy).
- **Why a separate stateful service?** Building the reference (online embedding clustering + A/V correlation) is a fundamentally different concern from *scoring belief* (Confidence Engine) and from *normalizing signals* (Fusion). SRP keeps heavy vector math and clustering state out of the latency-sensitive scorer. The system now has **two stateful services** — Identity Anchor and Confidence Engine — each partitioned by `meetingId`.
- **LLD components:** signal consumer → embedding buffer (per participant) → online clusterer (incremental centroid / streaming k-means) → A/V correlator (lip-motion vs VAD alignment) → anchor state machine → consistency-signal producer + `identity.anchor` producer. Hot state in Redis, durable snapshot in PostgreSQL (mirrors the Confidence Engine's recovery pattern).

## 4. Evidence Fusion Engine (Java / Spring Boot)

**Responsibility:** Normalise heterogeneous raw signals into a **uniform Evidence model** and correlate signals across modalities. This is the **anti-corruption layer** between messy AI outputs and the clean Confidence Engine.

- **Owns:** the `Evidence` schema; the mapping rules `signal → evidence(source, type, weight, reliability, polarity, ts, participantId)`; cross-signal corroboration (e.g., *face present* + *voice active* on the same participant is stronger than either alone); conflict detection (face says A, voice says B).
- **Does NOT own:** the running score or state — it emits point-in-time evidence, not beliefs.
- **Inputs:** `video.signals`, `audio.signals`, `meeting.events`, and the Identity Anchor Service's consistency signals (`FACE_CONSISTENT_WITH_ANCHOR`, `VOICE_CONSISTENT_WITH_ANCHOR`, `AV_BINDING`, `ANCHOR_MISMATCH`).
- **Outputs:** `evidence.events` — normalized, weighted, source-tagged.
- **Why separate from the Confidence Engine?** SRP + open/closed. Adding a new signal source means adding one adapter here; the Confidence Engine's scoring math never changes. Fusion logic (correlation) is distinct from belief-maintenance (scoring/decay).

## 5. Confidence Engine (Java / Spring Boot) — **STATEFUL**

**Responsibility:** Maintain the per-participant **belief** over time and emit the verdict.

- **Owns:** per-`(meetingId, participantId)` state: weighted evidence accumulator, time-decay, corroboration bonuses, contradiction penalties, hysteresis thresholds, and the resulting `confidenceScore` + `state`. Selection of the single most-likely candidate per meeting.
- **Does NOT own:** rendering reasons into English (Explanation Engine) or storing the timeline (Timeline Service).
- **Inputs:** `evidence.events`.
- **Outputs:** `confidence.updates` — `{meetingId, candidateId, score, state, contributions[], deltas, ts}`.
- **State store:** Redis (hot, per-partition) with periodic snapshots to PostgreSQL for durability/recovery. Detailed model in [05 — Confidence Engine](./05-confidence-engine.md).

## 6. Timeline Service (Java / Spring Boot)

**Responsibility:** Maintain an **immutable, ordered history** of what happened and why.

- **Owns:** append-only timeline entries (state transitions, notable evidence, score inflections); querying the timeline for a meeting.
- **Inputs:** `confidence.updates`, selected `evidence.events`.
- **Outputs:** `timeline.events` (for live push); persisted rows in PostgreSQL.
- **Why separate:** the timeline is a read-optimized projection with different retention/query needs than live scoring. Keeping it out of the Confidence Engine preserves that engine's latency budget.

## 7. Explanation Engine (Java / Spring Boot)

**Responsibility:** Convert numeric evidence contributions into **human-readable reasons** ("✓ Voice remained dominant", "⚠ Face changed at 12:04").

- **Owns:** reason templates, ranking of reasons by contribution, localization hooks, positive/negative framing.
- **Inputs:** `confidence.updates` (with contribution breakdown).
- **Outputs:** enriched verdict payload consumed by the WS Gateway / REST API.
- **Why separate:** explanation is presentation logic and evolves independently (wording, i18n, new templates) without touching scoring.

## 8. Notification Service (Java / Spring Boot)

**Responsibility:** React to **state transitions of interest** and alert humans/systems.

- **Owns:** alert rules (e.g., transition into `PROXY_SUSPECTED` or `CANDIDATE_SWITCHED`), channel adapters (email, webhook, in-app), deduplication/throttling.
- **Inputs:** `confidence.updates` (transitions), `notifications` topic.
- **Outputs:** external notifications; `notifications` audit records.

## 9. REST API (Spring Boot, part of the Edge layer)

**Responsibility:** Control-plane HTTP surface for clients. Commands and queries only — never runs AI or scoring inline.

- **Endpoints (representative):** create meeting, enrol expected candidate, start/stop monitoring, get current verdict, get timeline, list participants. Full contract in [08 — API Gateway](./08-api-gateway.md).

## 10. WebSocket Gateway (Spring WebSocket, part of the Edge layer)

**Responsibility:** Push live verdict and timeline updates to subscribed dashboard clients.

- **Owns:** WS session lifecycle, per-meeting subscription topics (STOMP destinations like `/topic/meetings/{id}/verdict`), auth handshake, fan-out from Kafka to clients.
- **Inputs:** `confidence.updates`, `timeline.events` (enriched by Explanation Engine).
- **Outputs:** WS frames to browsers.

---

## 11. Service Interaction Matrix

| Producer ↓ / Consumer → | Video | Audio | Anchor | Fusion | Confidence | Timeline | Explanation | Notify | WS GW |
|---|---|---|---|---|---|---|---|---|---|
| **Meeting Svc** (`meeting.events`) | – | – | ✓ | ✓ | – | – | – | – | – |
| **Ingestion** (`media.frames.meta`) | ✓ | ✓ | – | – | – | – | – | – | – |
| **Video Svc** (`video.signals`) | – | – | ✓ | ✓ | – | – | – | – | – |
| **Audio Svc** (`audio.signals`) | – | – | ✓ | ✓ | – | – | – | – | – |
| **Anchor** (`identity.anchor` + consistency signals) | – | – | – | ✓ | – | ✓ | – | ✓ | – |
| **Fusion** (`evidence.events`) | – | – | – | – | ✓ | ✓ | – | – | – |
| **Confidence** (`confidence.updates`) | – | – | – | – | – | ✓ | ✓ | ✓ | ✓ |
| **Explanation** (enriched verdict) | – | – | – | – | – | – | – | – | ✓ |

No cycles. Data flows strictly forward: **media → signals → {anchor, evidence} → confidence → {timeline, explanation, notification, UI}**. The Identity Anchor Service sits between the AI services and Fusion: it consumes raw embeddings and emits both anchor lifecycle events and consistency signals.

---

*Previous: [01 — HLD](./01-high-level-design.md) · Next: [03 — Event-Driven Design](./03-event-driven-design.md)*
