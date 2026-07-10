# 01 — High-Level Design (HLD)

## 1. System Context

```
                          ┌─────────────────────────────────────────────┐
                          │                 CLIENTS                      │
                          │                                              │
   Interviewer / Admin ──►│  Next.js Dashboard (React + Tailwind)        │
                          │   • Live verdict panel                       │
                          │   • Timeline                                 │
                          │   • Meeting controls                         │
                          └───────────────┬──────────────────────────────┘
                                          │ HTTPS (REST)  +  WSS (WebSocket)
                                          ▼
             ┌──────────────────────────────────────────────────────────────┐
             │                     EDGE / API LAYER (Spring Boot)             │
             │   ┌──────────────┐              ┌───────────────────────────┐  │
             │   │  REST API    │              │  WebSocket Gateway        │  │
             │   │  (commands,  │              │  (live verdict push,      │  │
             │   │   queries)   │              │   timeline stream)        │  │
             │   └──────┬───────┘              └───────────▲───────────────┘  │
             └──────────┼──────────────────────────────────┼─────────────────┘
                        │ produce/consume                   │ subscribe (verdict updates)
                        ▼                                   │
    ┌───────────────────────────────────  KAFKA  ──────────────────────────────────────┐
    │  Topics (partitioned by meetingId):                                                │
    │   meeting.events • media.frames.meta • video.signals • audio.signals •             │
    │   evidence.events • confidence.updates • timeline.events • notifications           │
    └───────┬───────────────┬───────────────┬─────────────┬───────────────┬─────────────┘
            │               │               │             │               │
            ▼               ▼               ▼             ▼               ▼
   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
   │  Meeting     │ │  Video Proc. │ │  Audio Proc. │ │  Evidence    │ │  Confidence  │
   │  Service     │ │  Service     │ │  Service     │ │  Fusion      │ │  Engine      │
   │  (Java)      │ │  (Python)    │ │  (Python)    │ │  Engine (Java)│ │  (Java,      │
   │              │ │  OpenCV,     │ │  pyannote,   │ │              │ │  STATEFUL)   │
   │              │ │  MediaPipe,  │ │  Silero VAD  │ │              │ │              │
   │              │ │  InsightFace │ │              │ │              │ │              │
   └──────────────┘ └──────────────┘ └──────────────┘ └──────┬───────┘ └──────┬───────┘
                                                             │                 │
                        ┌────────────────────────────────────┘                 │
                        ▼                                                       ▼
              ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
              │  Timeline    │   │  Explanation │   │ Notification │   │   (state)    │
              │  Service     │   │  Engine      │   │  Service     │   │  Redis + PG  │
              │  (Java)      │   │  (Java)      │   │  (Java)      │   │              │
              └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘

   ┌──────────────────────────── SHARED INFRASTRUCTURE ─────────────────────────────┐
   │   PostgreSQL (durable state, timeline, audit)   │   Redis (hot state, cache)    │
   │   Kafka (event backbone)   │   Object store / S3 (frame clips, embeddings)      │
   │   Observability: OpenTelemetry → Prometheus + Grafana + Loki + Jaeger           │
   └────────────────────────────────────────────────────────────────────────────────┘
```

## 2. The Two Planes

The system is deliberately split into two planes with different performance and consistency characteristics. Confusing them is the most common way these systems become unmaintainable.

### 2.1 Data Plane (high-throughput, async, eventually consistent)
The flow of media-derived signals: frames → detections → signals → evidence → confidence. This plane is **event-driven** end to end. It tolerates out-of-order and delayed events and never blocks on a synchronous call.

### 2.2 Control Plane (low-throughput, request/response, strongly consistent)
Human-facing operations: create a meeting, register a candidate's reference biometric, start/stop monitoring, fetch a verdict, fetch a timeline. This plane is **REST + WebSocket**, backed by PostgreSQL for consistency.

> **Why split them?** The data plane must scale to a firehose and degrade gracefully; the control plane must be correct and immediate. Different tools, different guarantees. Coupling them (e.g., processing frames inside a REST call) would make the API slow and the pipeline fragile.

## 3. Communication Model — When to use what

| Interaction | Mechanism | Why |
|---|---|---|
| Client → Backend commands/queries | **REST (HTTPS)** | Request/response, cacheable, standard auth, idempotent |
| Backend → Client live updates | **WebSocket (WSS)** | Server-push of verdict/timeline changes with low latency |
| AI service → Backend signals | **Kafka** | High volume, async, decoupled, replayable, backpressure-friendly |
| Service → Service (data plane) | **Kafka** | Loose coupling; producers don't know consumers; horizontal scale |
| Confidence Engine → hot state | **Redis** | Sub-millisecond read/write of per-participant belief |
| Durable persistence & audit | **PostgreSQL** | ACID, queryable history, timeline, evidence audit trail |
| Media ingestion (frames/audio) | **gRPC or Kafka (binary) + object store ref** | Large payloads: pass a reference, not the bytes, through Kafka |

> **Media payload rule:** Raw frames/audio chunks are **never** put on Kafka. The Meeting Service (or an ingestion adapter) writes media to the object store and publishes a lightweight `media.frames.meta` event carrying a reference (URI + timestamp + participantId). AI services fetch bytes by reference. This keeps Kafka fast and cheap.

## 4. End-to-End Event Flow (the "happy path")

```
1.  Meeting Service        → publishes  meeting.events        (participant JOINED)
2.  Ingestion adapter      → writes frame/audio to object store
                           → publishes  media.frames.meta      (ref + ts + participantId)
3.  Video Proc. Service    → consumes media meta, runs MediaPipe + InsightFace
                           → publishes  video.signals          (face embedding, lip-activity, quality)
4.  Audio Proc. Service    → consumes media meta, runs Silero VAD + pyannote
                           → publishes  audio.signals          (voice embedding, VAD, dominance)
4b. Identity Anchor Svc    → consumes video.signals + audio.signals (STATEFUL, zero-knowledge cold start)
                           → online-clusters embeddings + audio-visual binding → self-built anchor
                           → publishes  identity.anchor        (ANCHOR_LOCKED + *_CONSISTENT_WITH_ANCHOR, AV_BINDING)
5.  Evidence Fusion Engine → consumes video.signals + audio.signals + identity.anchor + meeting.events
                           → normalises each into an Evidence record (source, weight, reliability, ts)
                           → publishes  evidence.events
6.  Confidence Engine      → consumes evidence.events (STATEFUL, keyed by meetingId+participantId)
                           → updates per-participant belief, applies decay & hysteresis
                           → publishes  confidence.updates      (candidateId, score, state, contributions)
7.  Timeline Service       → consumes confidence.updates + key evidence.events
                           → appends immutable timeline entries; persists to PostgreSQL
8.  Explanation Engine     → consumes confidence.updates
                           → renders human-readable reasons from evidence contributions
                           → enriches the verdict payload
9.  Notification Service   → consumes confidence.updates (state transitions of interest)
                           → raises alerts (e.g., PROXY_SUSPECTED) via email/webhook/UI
10. WebSocket Gateway      → consumes confidence.updates + timeline.events
                           → pushes live verdict + timeline to subscribed dashboard clients
```

Every step is asynchronous and independently scalable. A slow or crashed consumer creates lag, not failure; Kafka retains events for replay.

## 5. Why Event-Driven (the explicit justification)

| Alternative considered | Why rejected |
|---|---|
| **Monolith with in-process calls** | Video/audio AI is Python; backend is Java. One deploy unit couples release cycles, languages, and scaling. A GPU-bound face model would throttle the REST API. |
| **Synchronous microservices (REST between services)** | The analysis path is a firehose; synchronous chains create head-of-line blocking, cascading timeouts, and tight coupling. Adding a new signal would require changing callers. |
| **Event-driven via Kafka (chosen)** | Producers and consumers evolve independently; new signals are just new producers on a topic; replayable for debugging/audit; natural backpressure; partitioning gives per-meeting ordering. |

## 6. Scaling Model

- **AI services (Video/Audio):** stateless workers in a Kafka consumer group. Add pods to increase throughput; partitions cap parallelism per topic. GPU node pool for InsightFace/pyannote.
- **Confidence Engine:** stateful but **partitioned** — each instance owns a subset of Kafka partitions, and thus a disjoint set of meetings. State for a `meetingId` lives on exactly one instance's Redis keyspace, avoiding cross-instance locks. Rebalance moves partition + state ownership together.
- **Backend edge (REST/WS):** stateless, scale horizontally behind a load balancer; WebSocket sessions are sticky or use a shared Redis pub/sub fan-out.

## 7. Deployment Topology (initial → future)

- **Initial (dev/demo):** Docker Compose — one container per service + Kafka + Redis + PostgreSQL + MinIO (S3-compatible).
- **Future (production):** Kubernetes on AWS. Kafka via MSK, PostgreSQL via RDS, Redis via ElastiCache, object store via S3, GPU node group for AI services, HPA on consumer lag.

---

*Previous: [00 — README](./README.md) · Next: [02 — Service Breakdown](./02-service-breakdown.md)*
