# 10 — Development Roadmap & Risk Management

## 1. Guiding Principle for Sequencing

Build the **thinnest end-to-end vertical slice first**, then deepen. We want a signal to travel from a mock producer all the way to a verdict on screen as early as possible — that proves the architecture before we invest in heavy AI models. Each milestone is independently demoable and testable.

## 2. Milestones

### M0 — Foundations & Contracts *(scaffolding)*
- Monorepo, Docker Compose infra (Kafka, Redis, PostgreSQL, MinIO), Makefile.
- `contracts/` proto schemas + Java/Python codegen.
- CI: build, test, lint, image publish. Health/metrics endpoints on every service.
- **Exit:** `docker-compose up` brings up the whole stack; a hello-world event flows Java→Kafka→Python and back.

### M1 — Control Plane: Meeting Service + REST API + DB
- Meeting/Participant/ExpectedCandidate aggregates, JPA, outbox producer.
- REST endpoints for create meeting / enrol candidate / start-stop.
- **Exit:** create a meeting and enrol a candidate via API; rows in PostgreSQL; `meeting.events` published.

### M2 — Skeleton Data Plane with Mock Signals
- Evidence Fusion Engine consuming `meeting.events` + **mock** `video.signals`/`audio.signals`.
- **Identity Anchor Service (mock):** simulate the OBSERVING → ANCHORING → LOCKED lifecycle from scripted embeddings, emitting `identity.anchor` + consistency signals — proves the zero-knowledge cold-start flow with no ML.
- Confidence Engine (stateful, Redis + snapshot) producing `confidence.updates`.
- A signal-simulator tool to inject scripted signals (cold-start, proxy, switch, camera-off, relay scenarios).
- **Exit:** injected signals drive OBSERVING → IDENTIFIED and the proxy/switch/relay states end-to-end. **This validates the whole architecture, including cold-start anchoring, without any ML.**

### M3 — WebSocket Gateway + Frontend MVP
- WS Gateway fan-out; Next.js dashboard: verdict panel + confidence gauge + reason list + state badge.
- **Exit:** live verdict visibly updates in the browser as the simulator injects signals.

### M4 — Timeline + Explanation + Notification
- Timeline Service (persist + push), Explanation Engine (reason rendering), Notification Service (proxy/switch alerts).
- **Exit:** timeline populates live; proxy scenario raises a CRITICAL alert; reasons render in English.

### M5 — Real Video Processing
- MediaPipe detection + lip-activity, InsightFace embedding, quality scoring.
- Replace mock video signals with real embeddings.
- **Exit:** a real face on webcam produces real face embeddings + lip-activity into `video.signals`.

### M6 — Real Audio Processing
- Silero VAD, pyannote diarization/embedding, dominance measurement.
- Replace mock audio signals with real embeddings.
- **Exit:** real voice produces real voice embeddings + VAD + dominance.

### M6.5 — Real Identity Anchor Service (the zero-knowledge core)
- Online embedding clustering, audio-visual binding (lip-motion ↔ VAD correlation), anchor lifecycle + drift detection, Redis hot state + PostgreSQL snapshots.
- Optional reference-anchored mode wired to a live ID-capture at join.
- **Exit:** with **no pre-uploaded reference**, a real interview self-anchors and reaches IDENTIFIED; a mid-call switch or an off-screen-relay attempt trips PROXY_SUSPECTED.

### M7 — Hardening the Confidence Model
- Calibrate thresholds/weights on recorded scenarios; tune decay half-lives; corroboration/contradiction tuning; false-positive test suite.
- **Exit:** documented accuracy on a scenario test set (proxy, switch, camera-off, multi-speaker); false-positive rate below target.

### M8 — Production Readiness
- Observability dashboards (lag, latency, verdict distribution), autoscaling on Kafka lag, DLT handling, security review, load test, resiliency (chaos: kill a consumer, verify recovery).
- **Exit:** survives component failures with correct recovery; meets latency/scaling targets.

### M9 (future) — Kubernetes / AWS
- Helm charts, EKS, MSK, RDS, ElastiCache, S3, GPU node group, HPA.

> **Recommendation:** M0→M4 is a strong internship deliverable that demonstrates the full architecture end-to-end with the simulator; M5→M7 add the real AI. Prioritize a working skeleton over a half-finished model.

## 3. Risks & Mitigations

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | **AI accuracy insufficient** (bad lighting, accents) → wrong verdicts | High | High | Multi-signal fusion (never one model); reliability weighting; prefer UNCERTAIN; calibrate on a scenario test set (M7). |
| R2 | **False positives** wrongly flag honest candidates | Med | Very High | Hysteresis, separation + corroboration requirements, contradiction penalties, asymmetric thresholds (doc 05 §6). |
| R3 | **Latency/throughput** — pipeline can't keep up in real time | Med | High | Adaptive frame sampling, Kafka backpressure, autoscale on lag, GPU workers, batched inference. |
| R4 | **Stateful engine correctness** under rebalance/crash | Med | High | Snapshot + offset replay recovery; idempotent, time-based (not incremental) updates; partition=meeting ownership. |
| R5 | **Media handling cost** (frames through Kafka) | Med | Med | Never put bytes on Kafka; object-store refs only; short retention on media topics. |
| R6 | **Schema drift** between Java and Python producers/consumers | Med | Med | Single `contracts/` module + schema registry + codegen for both languages. |
| R7 | **Privacy / biometric data handling** (legal + ethical) | High | High | Encrypt biometrics at rest, short retention, access controls, consent capture, data-deletion path, audit log. Treat as PII. |
| R8 | **Adversarial evasion** (deepfake, replayed audio) | Low→Med | High | Liveness checks (future), cross-modal corroboration (hard to fake face+voice+behaviour together), anomaly alerts. |
| R9 | **Meeting-platform integration** variance (Zoom/Meet/Teams APIs) | Med | Med | Abstract ingestion behind an adapter interface; start with a controlled capture source; add platform adapters incrementally. |
| R10 | **Scope creep** in a time-boxed challenge | High | Med | Milestone gating with demoable exits; simulator lets us prove architecture before ML; ASR/"answered N questions" via transcription is explicitly deferred. |
| R11 | **Over-engineering** for the challenge timeline | Med | Med | Monorepo + Compose keeps ops light; K8s/AWS are explicitly future (M9). |

## 4. Non-Goals (v1)

- Full speech-to-text / question-answer attribution via ASR (dominance is used as a proxy for "answered questions"; true ASR-based Q&A is a future add-on that plugs in as a new signal producer).
- Deepfake-grade liveness defense (basic heuristics only in v1).
- Native meeting-platform bots for every provider (one ingestion path first; adapters later).
- Multi-tenant billing / org management.

## 5. Definition of Done (per milestone)

Every milestone is "done" only when: code + unit/integration tests pass in CI, the exit demo works via `docker-compose up`, the relevant doc section is updated, and observability (logs/metrics/traces) exists for the new components. No milestone is done because "it runs on my machine."

## 6. Testing Strategy (cross-cutting)

- **Unit:** domain logic (scoring math, state classifier, evidence mapping) — pure, fast, no infra.
- **Contract tests:** producers/consumers validated against `contracts/` schemas.
- **Integration:** Testcontainers (Kafka/Postgres/Redis) for each service.
- **Scenario tests:** the simulator replays proxy / switch / camera-off / multi-speaker / network-drop scripts and asserts the verdict + state transitions and the **false-positive rate**.
- **Load tests:** many concurrent meetings; assert lag and latency budgets.

---

*Previous: [09 — Folder Structures](./09-folder-structures.md) · Back to [README](./README.md)*
