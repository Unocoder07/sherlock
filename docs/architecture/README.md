# Sherlock — Interview Identity System
## Architecture Documentation (v1.0)

> **Status:** DRAFT — awaiting approval before implementation begins.
> **Audience:** Senior engineers building and operating the system.
> **Scope:** This document set is the single source of truth for the system design. No application code exists yet.

---

## 1. The Problem in One Sentence

During a live online interview, **determine which meeting participant is the real, intended candidate** — continuously, in real time, with a confidence score and a human-readable justification — while being robust to proxies, candidate-switching, cameras off, audio drops, and generic display names.

## 2. Why This Is Hard (and why metadata alone fails)

The naive approach is to trust the meeting platform's participant list: "The person named *John Doe* is John Doe." This fails constantly in the real world:

| Attack / Edge case | Why metadata fails |
|---|---|
| **Proxy interview** | A stand-in joins under the candidate's name and answers questions. |
| **Candidate switching** | Real candidate joins, answers easy questions, hands laptop to an expert for the hard ones. |
| **Generic display names** | Participant shows as `iPhone`, `Guest`, `User`, `Galaxy S23`. |
| **Camera off** | No face signal; must fall back to voice + behaviour. |
| **Audio drop / network blips** | Signals vanish temporarily and must not collapse confidence. |

**Design principle:** Identity is inferred from a **fusion of independent biometric and behavioural signals**, where meeting metadata is the *weakest* input, used only as a tie-breaker or prior. The system builds an evidence-based belief, not a lookup.

## 3. The Core Idea — Evidence Fusion

We treat identity as a **probabilistic belief maintained over time**, updated by a stream of weak, noisy signals:

```
Signals (weak, noisy, async)                Belief (strong, explainable)
───────────────────────────                 ────────────────────────────
Face embedding match      ─┐
Face continuously present  │
Speaker is active          ├──►  EVIDENCE   ──►  CONFIDENCE  ──►  Candidate: John Doe
Voice embedding consistent │      FUSION         ENGINE          Confidence: 94%
Shared screen during code  │      ENGINE        (stateful)       State: IDENTIFIED
Join/leave meeting events  │                                     Reasons: [ ... ]
Metadata (name) [lowest]  ─┘
```

Each signal is a piece of **evidence** with a source, a weight, a reliability, and a timestamp. The Confidence Engine maintains **per-participant state**, decays stale evidence, and computes a score plus the human-readable reasons behind it.

## 3.1 Zero-Knowledge Cold Start — Progressive Identity Anchoring

**The system starts with ZERO biometric knowledge of the candidate.** We do *not* assume the interviewer uploads a reference photo/voice — in a real interview, no such trusted reference exists (the invite name is just metadata, which we distrust by design). Instead, Sherlock **builds the reference from the interview itself** and then monitors consistency against that self-built anchor.

```
OBSERVING          →   ANCHORING                 →   IDENTIFIED / MONITORING
(0 knowledge)          (build self-reference)         (check consistency vs self-reference)

collect face &         online-cluster embeddings;     same face present throughout?
voice embeddings       BIND the dominant              same voice answering questions?
from all participants  FACE ↔ VOICE via               did the identity drift / switch?
                       lip-sync correlation;          is the visible person the speaker?
                       lock a provisional anchor      → verdict + reasons
```

**The key mechanism that replaces the reference photo is _audio-visual binding_:** correlate lip movement with the active voice so the system *knows the person on camera is the one answering*. This alone defeats the "one person on camera, an expert answering off-screen" relay — with no prior reference.

### The honest limitation (and how we resolve it)

Self-anchoring reliably catches **candidate switching, multiple people, face≠voice relay, and camera-off-then-someone-else**. It *cannot*, by itself, catch a proxy who is present and consistent from the very first second — a consistent wrong person still looks "consistent." Upgrading from *"a consistent person"* to *"the legitimate candidate"* requires an external truth anchor **at least once**. Hence two modes, **neither of which asks the interviewer to pre-upload media**:

| Mode | Where the reference comes from | Guarantee |
|---|---|---|
| **Self-anchored** (default) | Built live from the interview | Detects switching / relay / inconsistency |
| **Reference-anchored** (optional) | Captured **live at join** (quick ID-card + face/voice capture), or a verified prior screening call | True verification: "this *is* the named candidate" |

The **Identity Anchor Service** (a stateful service, see doc 02) owns building and maintaining this anchor; the Confidence Engine consumes "consistency-with-anchor" evidence from it. In both modes the Confidence Engine math is identical — only the *source* of the anchor differs.

## 4. Document Index

Read in order. Each document is self-contained but cross-references the others.

| # | Document | Covers (of the 17 required deliverables) |
|---|---|---|
| 00 | **README** (this file) | Problem framing, core idea, index |
| 01 | [High-Level Design](./01-high-level-design.md) | HLD, event flow overview, communication model |
| 02 | [Service Breakdown & Responsibilities](./02-service-breakdown.md) | Service breakdown, responsibilities, per-service LLD |
| 03 | [Event-Driven Design](./03-event-driven-design.md) | Event flow, Kafka topics, schemas, async processing |
| 04 | [Database Design](./04-database-design.md) | Schema, tables, indexes, partitioning, Redis usage |
| 05 | [Confidence Engine](./05-confidence-engine.md) | Stateful scoring model, weighting, decay, false-positive control |
| 06 | [State Machine](./06-state-machine.md) | Candidate lifecycle states and transitions |
| 07 | [Sequence Diagrams](./07-sequence-diagrams.md) | PlantUML sequences for key flows |
| 08 | [API Gateway & Contracts](./08-api-gateway.md) | Gateway design, REST + WebSocket contracts, auth |
| 09 | [Folder Structures](./09-folder-structures.md) | Backend, AI services, frontend, git repo layout |
| 10 | [Roadmap & Risks](./10-roadmap-and-risks.md) | Milestones, risks & mitigations, non-goals |

## 5. Quality Attributes (what "good" means here)

These are the ranked, testable goals every design decision is measured against.

| Attribute | Concrete target | How the architecture delivers it |
|---|---|---|
| **Explainability** | Every verdict carries ≥3 human-readable reasons with contributing weights | Evidence is first-class; Explanation Engine renders it |
| **Reliability** | No single signal outage collapses the verdict; graceful degradation | Multi-signal fusion + evidence decay, not hard dependencies |
| **Low false positives** | Prefer `UNCERTAIN` over a wrong `IDENTIFIED` | Hysteresis thresholds + corroboration rules in Confidence Engine |
| **Scalability** | Horizontally scale per-service; 100s of concurrent meetings | Stateless AI workers, partitioned Kafka by `meetingId` |
| **Performance** | Verdict updates ≤2s after a signal; frame processing near real-time | Async pipeline, Redis hot state, backpressure |
| **Maintainability** | New signal source added without touching the Confidence Engine core | Signal → Evidence adapter pattern, open/closed scoring |

## 6. Guiding Architectural Principles

1. **Event-driven, not request-driven** for the analysis path. Video/audio produce a firehose of signals; a synchronous call chain would be brittle and slow. Services publish/consume events via Kafka.
2. **Stateless compute, stateful belief.** AI services (Video/Audio) are stateless and horizontally scalable. The **two stateful services** are the **Identity Anchor Service** (holds the self-built reference) and the **Confidence Engine** (holds the belief) — both keep hot state in Redis with durable PostgreSQL snapshots, both partitioned by `meetingId`.
3. **Partition by `meetingId`.** All events for one meeting are ordered and processed together, enabling correct temporal fusion without cross-meeting locking.
4. **Fail open, degrade gracefully.** A missing signal lowers *certainty*, it does not crash the verdict. Evidence decays; it is never assumed.
5. **Metadata is a prior, never proof.** Enforced by the weighting model (see doc 05).
6. **Clean boundaries.** Each service owns its domain; contracts are versioned event schemas and DTOs, never shared mutable state.

---

*Next: [01 — High-Level Design](./01-high-level-design.md)*
