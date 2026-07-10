# 09 — Folder Structures

Layouts follow Clean Architecture / DDD: dependencies point **inward** (adapters → application → domain). Domain has zero framework dependencies.

## 1. Git Repository Structure (monorepo)

A **monorepo** is recommended for this challenge: atomic cross-service changes (a new event schema touches Java + Python + contracts at once), one CI, easy local `docker-compose up`. Services remain independently deployable (separate images).

```
sherlock/
├── README.md
├── docker-compose.yml                 # full local stack
├── docker-compose.infra.yml           # kafka, redis, postgres, minio only
├── .env.example
├── Makefile                           # up/down/test/lint shortcuts
├── docs/
│   └── architecture/                  # THIS document set
├── contracts/                         # single source of truth for events
│   ├── proto/                         # .proto event & DTO definitions
│   ├── gen-java/                      # generated Java stubs (build artifact)
│   └── gen-python/                    # generated Python stubs (build artifact)
├── backend/                           # Java 21 / Spring Boot (Gradle multi-module)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── libs/                          # shared internal libraries
│   │   ├── common-domain/
│   │   ├── common-kafka/
│   │   └── common-web/
│   └── services/
│       ├── meeting-service/
│       ├── identity-anchor-service/   # STATEFUL: self-built reference (zero-knowledge cold start)
│       ├── evidence-fusion-service/
│       ├── confidence-engine/
│       ├── timeline-service/
│       ├── explanation-engine/
│       ├── notification-service/
│       └── edge-gateway/              # REST API + WebSocket Gateway + Spring Security
├── ai-services/                       # Python 3.11 / FastAPI
│   ├── video-processing/
│   ├── audio-processing/
│   └── shared/                        # common python lib (kafka, models, config)
├── frontend/                          # Next.js + React + Tailwind
├── infra/
│   ├── k8s/                           # future: Helm charts / manifests
│   ├── terraform/                     # future: AWS (MSK, RDS, ElastiCache, S3, EKS)
│   └── local/                         # init scripts, kafka topic creation
└── .github/workflows/                 # CI: build, test, lint, image publish
```

## 2. Backend — per-service layout (Clean Architecture)

Example: `confidence-engine` (all Java services follow this shape).

```
confidence-engine/
├── build.gradle.kts
└── src/main/java/com/sherlock/confidence/
    ├── ConfidenceEngineApplication.java
    ├── domain/                        # PURE domain — no Spring, no Kafka
    │   ├── model/                     # ParticipantBelief, EvidenceRecord, Verdict, State (value objects/entities)
    │   ├── policy/                    # WeightPolicy, DecayPolicy (Strategy pattern)
    │   ├── service/                   # ConfidenceCalculator, StateClassifier (domain services)
    │   └── event/                     # domain events
    ├── application/                   # use cases / orchestration
    │   ├── port/
    │   │   ├── in/                    # inbound ports (use-case interfaces)
    │   │   └── out/                   # outbound ports (StateRepository, EventPublisher) — interfaces
    │   ├── usecase/                   # ApplyEvidenceUseCase, RecoverStateUseCase
    │   └── dto/
    ├── adapter/                       # framework-facing (implements ports)
    │   ├── in/kafka/                  # evidence.events consumer
    │   ├── out/kafka/                 # confidence.updates producer
    │   ├── out/redis/                 # hot-state repository impl
    │   └── out/persistence/           # JPA snapshot repository impl (entities + mappers)
    ├── config/                        # Spring config, beans, Kafka/Redis config
    ├── exception/                     # domain + web exceptions, handlers
    ├── constants/
    └── util/
```

**Dependency rule enforced:** `domain` depends on nothing; `application` depends on `domain`; `adapter`/`config` depend on `application` via ports. Swapping Redis or Kafka never touches domain logic.

`edge-gateway` additionally has:
```
edge-gateway/src/main/java/com/sherlock/edge/
├── rest/          # @RestController classes (thin) → application services
├── websocket/     # STOMP config, handlers, subscription auth
├── security/      # Spring Security, JWT, RBAC, CORS
├── dto/           # request/response DTOs (never expose entities)
└── config/
```

## 3. AI Services — per-service layout (Python / FastAPI)

Example: `video-processing` (audio mirrors it).

```
video-processing/
├── pyproject.toml
├── Dockerfile
└── app/
    ├── main.py                        # FastAPI app (health/metrics) + consumer bootstrap
    ├── domain/                        # framework-free processing logic
    │   ├── detectors/                 # face_detector.py (MediaPipe), embedder.py (InsightFace)
    │   ├── matcher.py                 # cosine match vs reference
    │   ├── quality.py                 # blur/pose/illumination
    │   └── signal_builder.py          # raw result -> video.signals payload
    ├── application/
    │   └── process_frame.py           # use case: meta -> fetch -> detect -> signal
    ├── adapters/
    │   ├── kafka_consumer.py          # media.frames.meta
    │   ├── kafka_producer.py          # video.signals
    │   └── object_store.py            # fetch frame bytes by ref
    ├── infrastructure/
    │   ├── model_registry.py          # singleton model loading (GPU-aware)
    │   ├── config.py                  # pydantic settings
    │   └── telemetry.py               # OTel metrics/tracing
    └── constants.py
tests/
```

**Why FastAPI when the hot path is Kafka, not HTTP?** FastAPI hosts health/readiness/metrics endpoints and an optional synchronous debug API for a single frame; the throughput path runs as an async Kafka consumer in the same process. Uniform ops surface, minimal overhead.

## 4. Frontend — Next.js layout

```
frontend/
├── package.json
├── next.config.js
├── tailwind.config.ts
└── src/
    ├── app/                           # Next.js App Router
    │   ├── layout.tsx
    │   ├── page.tsx                   # meeting list
    │   └── meetings/[id]/page.tsx     # live dashboard
    ├── components/
    │   ├── verdict/                   # VerdictPanel, ConfidenceGauge, ReasonList
    │   ├── timeline/                  # TimelineView, TimelineEntry
    │   ├── participants/              # ParticipantList, ParticipantCard, StateBadge
    │   └── ui/                        # design-system primitives
    ├── features/                      # feature-sliced logic
    │   ├── verdict/                   # hooks, selectors, types
    │   └── timeline/
    ├── lib/
    │   ├── api/                       # REST client (typed)
    │   ├── ws/                        # STOMP/WebSocket client + reconnection
    │   └── types/                     # shared TS types (generated from contracts)
    ├── hooks/                         # useVerdictStream, useTimeline
    └── styles/
```

Frontend types are **generated from the same `contracts/` schemas**, so the UI never drifts from the backend event shapes.

---

*Previous: [08 — API Gateway](./08-api-gateway.md) · Next: [10 — Roadmap & Risks](./10-roadmap-and-risks.md)*
