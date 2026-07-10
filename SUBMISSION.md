# Sherlock — Interview Identity System

### Project Submission

|                |                                              |
| -------------- | -------------------------------------------- |
| **Author**     | [Your Name]                                  |
| **Email**      | [your.email@example.com]                     |
| **GitHub**     | [@Unocoder07](https://github.com/Unocoder07) |
| **Repository** | https://github.com/Unocoder07/sherlock       |
| **Live Demo**  | 🔴 **[Open the live dashboard](LIVE_URL)**   |
| **LinkedIn**   | [your-linkedin-url]                          |
| **Submitted**  | July 2026                                    |

---

## 1. Executive Summary

**Sherlock is a real-time system that verifies _who is actually sitting the interview_ — continuously, with a confidence score and a human-readable justification — without ever being given a reference photo or voice sample up front.**

Online interviews are easy to cheat: a stand-in ("proxy") can join under the candidate's
name, the real candidate can hand the laptop to an expert for the hard questions, or the
participant can simply show up as `Guest` with the camera off. Trusting the meeting
platform's participant list ("the person named _John Doe_ is John Doe") fails against all
of these.

Sherlock takes a fundamentally different approach: it **infers identity from a fusion of
independent biometric and behavioural signals** (face, voice, speaker activity, lip-sync,
meeting events), treats meeting metadata as the _weakest_ input, and **builds the
candidate's reference identity from the interview itself** — then monitors consistency
against that self-built anchor for the rest of the session.

The output is a live verdict per participant:

> **Candidate: John Doe · Confidence: 94% · State: IDENTIFIED**
> _Reasons: ✓ Voice stayed dominant · ✓ Same face present throughout · ✓ Lip-sync consistent_

---

## 2. The Problem

| Attack / Edge case              | Why naive metadata fails                                                |
| ------------------------------- | ----------------------------------------------------------------------- |
| **Proxy interview**             | A stand-in joins under the candidate's name and answers questions.      |
| **Candidate switching**         | Real candidate answers the easy questions, then hands off to an expert. |
| **Generic display names**       | Participant shows as `iPhone`, `Guest`, `User`, `Galaxy S23`.           |
| **Camera off**                  | No face signal — the system must fall back to voice + behaviour.        |
| **Audio drops / network blips** | Signals vanish temporarily and must **not** collapse the verdict.       |

**Design principle:** identity is a _probabilistic belief maintained over time_, not a
database lookup. Meeting metadata is used only as a weak tie-breaker.

---

## 3. How It Works

### 3.1 Evidence Fusion

Each incoming signal is a piece of **evidence** with a source, weight, reliability, and
timestamp. A stateful **Confidence Engine** maintains per-participant belief, decays stale
evidence over time, and computes a score plus the reasons behind it.

```
Signals (weak, noisy, async)                 Belief (strong, explainable)
──────────────────────────                   ────────────────────────────
Face embedding match      ─┐
Face continuously present  │
Speaker is active          ├──►  EVIDENCE  ──►  CONFIDENCE  ──►  Candidate: John Doe
Voice consistency          │      FUSION        ENGINE          Confidence: 94%
Lip-sync (face↔voice)      │                   (stateful)       State: IDENTIFIED
Meeting events             │                                    Reasons: [ ... ]
Metadata (name) [weakest] ─┘
```

### 3.2 Zero-Knowledge Cold Start (the hard part)

Sherlock starts with **zero biometric knowledge** of the candidate — no uploaded
reference. It builds the reference _during_ the interview and then checks consistency
against it, moving through a state machine:

```
OBSERVING          →   ANCHORING                    →   IDENTIFIED / MONITORING
(0 knowledge)          (build self-reference)            (check consistency vs anchor)
collect face & voice   online-cluster embeddings;        same face throughout?
embeddings from all    bind dominant FACE ↔ VOICE        same voice answering?
participants           via lip-sync; lock anchor         did identity drift / switch?
```

### 3.3 Verdict States

`OBSERVING` · `ANCHORING` · `UNCERTAIN` · **`IDENTIFIED`** · **`PROXY_SUSPECTED`** ·
**`CANDIDATE_SWITCHED`** · `SIGNAL_LOST` · `LEFT`

---

## 4. System Architecture

Sherlock is an **event-driven microservice system**. Every service is decoupled through a
Kafka event bus with Protobuf-defined schemas as the single source of truth. Backend
services follow a **hexagonal (ports & adapters) architecture**.

```
                                  ┌──────────────────────────┐
   Webcam / Signal Simulator ───► │        Kafka bus         │
                                  │  (Protobuf event schemas)│
                                  └──────────────────────────┘
                                      │        │        │
              ┌───────────────────────┘        │        └────────────────────┐
              ▼                                 ▼                             ▼
   ┌───────────────────┐   ┌────────────────────┐   ┌──────────────────┐   ┌────────────────┐
   │ Video Processing  │   │ Identity Anchor    │   │ Evidence Fusion  │   │ Meeting Service│
   │ (InsightFace ML)  │   │ (cold-start anchor)│   │ (signal→evidence)│   │ (REST + DB)    │
   └───────────────────┘   └────────────────────┘   └──────────────────┘   └────────────────┘
                                      │
                                      ▼
                          ┌────────────────────┐
                          │  Confidence Engine │  ── stateful belief, decay, scoring
                          └────────────────────┘
                            │        │         │
                            ▼        ▼         ▼
                ┌─────────────┐ ┌──────────┐ ┌──────────────┐
                │ Explanation │ │ Timeline │ │ Notification │
                │ (English    │ │ (history │ │ (proxy/switch│
                │  reasons)   │ │  in DB)  │ │  CRITICAL)   │
                └─────────────┘ └──────────┘ └──────────────┘
                            │        │         │
                            └────────┴─────────┘
                                     ▼
                          ┌────────────────────┐        ┌──────────────────┐
                          │   Edge Gateway     │ ─WS──► │  Next.js Frontend│
                          │ (STOMP-over-WS)    │        │  (live dashboard)│
                          └────────────────────┘        └──────────────────┘
```

### Services

| Service                  | Responsibility                                                  | Tech                                    |
| ------------------------ | --------------------------------------------------------------- | --------------------------------------- |
| **Meeting Service**      | Meeting lifecycle, REST API, persistence                        | Java 21 / Spring Boot / Postgres        |
| **Video Processing**     | Face detection + 512-d embeddings from webcam frames            | Python / InsightFace / OpenCV           |
| **Identity Anchor**      | Zero-knowledge cold-start; builds the self-reference            | Java / Spring Boot                      |
| **Evidence Fusion**      | Maps raw signals → weighted evidence records                    | Java / Spring Boot                      |
| **Confidence Engine**    | Stateful per-participant belief, decay, verdict + state machine | Java / Spring Boot / Redis              |
| **Explanation Engine**   | Renders numeric contributions into English reasons              | Java / Spring Boot                      |
| **Timeline Service**     | Append-only history of transitions & score inflections          | Java / Spring Boot / Postgres           |
| **Notification Service** | Raises CRITICAL alerts (proxy/switch), audit trail              | Java / Spring Boot / Postgres           |
| **Edge Gateway**         | Fans verdicts out over STOMP-over-WebSocket                     | Java / Spring Boot                      |
| **Frontend**             | Live dashboard: verdict, gauge, timeline, alerts                | Next.js / React / TypeScript / Tailwind |

---

## 5. Technology Stack

- **Backend:** Java 21, Spring Boot, Gradle multi-module, **hexagonal architecture**
- **Event bus:** Apache Kafka, **Protobuf** schemas via [buf](https://buf.build), Confluent Schema Registry
- **AI / ML:** Python, InsightFace (face embeddings), OpenCV
- **Data:** PostgreSQL, Redis, MinIO (S3-compatible object store for frames/embeddings)
- **Frontend:** Next.js, React, TypeScript, Tailwind CSS, STOMP-over-WebSocket
- **Infrastructure:** Docker & Docker Compose (16-container local stack)

---

## 6. What's Built (Milestones)

The system was built **incrementally by milestone**, each with a demoable exit criterion.
A key architectural decision was a **signal simulator** that drives the entire data plane
with scripted signals — proving the full end-to-end architecture _before_ wiring in the
heavy ML, and keeping every milestone independently demoable.

| Milestone | Deliverable                                                                | Status           |
| --------- | -------------------------------------------------------------------------- | ---------------- |
| **M0**    | Foundations & Protobuf contracts; Kafka produce→consume smoke test         | ✅ Complete      |
| **M1**    | Meeting Service + REST API + Postgres                                      | ✅ Complete      |
| **M2**    | Skeleton data plane (mock anchor → fusion → confidence) + signal simulator | ✅ Complete      |
| **M3**    | WebSocket gateway fan-out + Next.js live dashboard                         | ✅ Complete      |
| **M4**    | Timeline + English explanation engine + proxy CRITICAL alerts              | ✅ Complete      |
| **M5**    | Real video AI: webcam → MinIO → InsightFace embeddings → signals           | ✅ Code-complete |
| **M6+**   | Real audio / identity-anchor AI, model hardening, production readiness     | 🔜 Roadmap       |

**Milestones M0–M4 are fully demoable end-to-end today** with zero ML, driven by the
signal simulator. M5 (real webcam face processing) is implemented and containerised.

---

## 7. Running the Project

**Prerequisite:** Docker Desktop.

```bash
# 1) Clone
git clone https://github.com/Unocoder07/sherlock.git
cd sherlock
cp .env.example .env

# 2) Bring up the stack (infra + all services + dashboard)
docker compose up -d

# 3) Open the dashboard
#    http://localhost:3000
#    paste a meeting id (any UUID) and click "Watch" — status dot turns green (Live)

# 4) Drive a live verdict into that meeting with the signal simulator (no ML needed)
cd backend
./gradlew :tools:signal-simulator:run --args="--scenario cold-start --meeting <the-uuid>"
```

Watch the dashboard update **live**: the state badge climbs `OBSERVING → UNCERTAIN →
IDENTIFIED`, the confidence gauge rises, and the reason bars populate.

### Demo scenarios

| Scenario          | Result                               |
| ----------------- | ------------------------------------ |
| `cold-start`      | → **✓ IDENTIFIED**                   |
| `proxy` / `relay` | → **! PROXY_SUSPECTED** (red banner) |
| `switch`          | → **⇄ CANDIDATE_SWITCHED**           |
| `camera-off`      | held by voice — no false proxy       |

> **Note on hardware:** the full 16-container stack (incl. the InsightFace ML service)
> needs ~8 GB+ of RAM headroom. On a constrained machine, run
> `docker compose up -d --scale video-processing=0` and use the signal simulator — this
> demonstrates the complete architecture and live verdict flow without the ML service.

---

## 8. Engineering Highlights

- **Event-driven, not request-driven** — every service communicates through Kafka topics,
  so the pipeline is decoupled, replayable, and horizontally scalable.
- **Contract-first** — Protobuf schemas are the single source of truth; Java and Python
  stubs are generated, so services can never drift out of sync on the wire format.
- **Hexagonal architecture** — domain logic is isolated behind ports; Kafka, Postgres, and
  Redis are swappable adapters. Domain code has no framework imports.
- **Explainability by design** — the verdict is never a black box; every score carries the
  weighted contributions that produced it, rendered into English reasons.
- **Zero-knowledge cold start** — the system's hardest and most novel piece: building a
  trusted identity reference from an untrusted live stream.
- **Resilience to signal loss** — evidence decays gracefully; a dropped camera or audio
  blip does not collapse a confident verdict into a false alarm.
- **Testable in isolation** — the signal simulator replays proxy / switch / camera-off /
  network-drop scripts and asserts the resulting state transitions.

---

## 9. Repository Layout

```
sherlock/
├── docs/architecture/     # Full HLD/LLD design docs (11 documents — the design source of truth)
├── contracts/             # Protobuf event schemas (single source of truth for the bus)
├── backend/               # Java 21 / Spring Boot services (Gradle multi-module, hexagonal)
│   └── tools/signal-simulator/   # Scripted-scenario driver (no ML)
├── ai-services/           # Python AI workers (video processing / InsightFace)
├── frontend/              # Next.js + React + Tailwind live dashboard
├── infra/local/           # Postgres schemas, Kafka topics, MinIO buckets
├── docker-compose.yml     # Full local stack
└── README.md              # Detailed per-milestone build & demo guide
```

📐 **Deep-dive design docs:** [`docs/architecture/`](./docs/architecture/README.md) —
high-level design, service breakdown, event-driven design, database design, the confidence
engine, the state machine, sequence diagrams, the API gateway, and the roadmap.

---

## 10. Demo

- 🔴 **Live demo (hosted):** **[<LIVE_URL>](LIVE_URL)** — open the URL, paste a meeting
  id (any UUID), and click **Watch**; the status dot turns green (Live) and the verdict
  updates in real time.
- 🎥 **Demo video:** [(https://drive.google.com/file/d/1Pv5huXFPA9MAlmlK7LCf6hnjTd2CQJvi/view?usp=drivesdk)]
