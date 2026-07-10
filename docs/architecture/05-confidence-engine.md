# 05 — Confidence Engine (Stateful Scoring Model)

This is the intellectual core of Sherlock. It maintains a **belief** about each participant's identity over time and produces the verdict. Design goals, in order: **explainable**, **low false-positive**, **robust to missing signals**, **fast**.

## 1. Mental Model

For each participant `p` in a meeting, we maintain a running **identity belief** — a confidence in `[0,1]` that `p` is the *expected candidate*. Belief is a **time-decayed, weighted, reliability-scaled sum of evidence**, normalized, then passed through a **hysteresis state classifier**.

We deliberately choose an **interpretable additive/log-odds model over a black-box classifier** because the challenge demands *explainability* — every point of confidence must trace to named evidence. A neural net verdict we cannot justify is a failed requirement, not a better model.

## 1.1 Zero-Knowledge Cold Start (no reference required)

The Confidence Engine never assumes a pre-uploaded reference. It scores **consistency against the self-built Identity Anchor** produced by the Identity Anchor Service (doc 02 §3.1), which the system learns *during* the interview:

- **Warm-up (`INITIALIZING`/OBSERVING):** the anchor is still forming, so identity evidence has **low reliability**; the engine holds the participant in `UNCERTAIN`/`INITIALIZING` and deliberately refuses to declare `IDENTIFIED`. No premature verdict from thin data.
- **Anchor locked:** the anchor emits `*_CONSISTENT_WITH_ANCHOR` and `AV_BINDING` signals with rising reliability. These flow through Fusion as normal evidence, and the belief climbs toward `IDENTIFIED`.
- **The `FACE_MATCH` / `VOICE_MATCH` evidence below means "match against the anchor"** — the anchor is *self-built by default*, or *seeded from an optional live-captured reference* (reference-anchored mode). The scoring math is identical either way; only the anchor's provenance and its ceiling reliability differ (a verified reference permits higher confidence than a self-built one).

This is exactly the "system starts with 0 info, gains candidate info as the interview proceeds, then gives feedback" behaviour: confidence is *low by construction* until the anchor exists, then grows as consistency accumulates.

## 2. Evidence Weighting (the ranked signal hierarchy)

Each evidence type has a **base weight** `w`. Metadata is deliberately near the bottom.

| Evidence type | Base weight `w` | Polarity | Rationale |
|---|---|---|---|
| `AV_BINDING` (visible face is the active speaker) | **0.30** | + | Cold-start keystone: binds face↔voice with no reference; defeats off-screen relay |
| `FACE_MATCH` (consistency vs **anchor**) | **0.28** | + | Same person on camera as the anchor |
| `VOICE_MATCH` (consistency vs **anchor**) | **0.24** | + | Same voice as the anchor; carries belief when camera off |
| `SPEAKING / DOMINANCE` | **0.15** | + | Real candidate answers most questions |
| `FACE_PRESENT (continuity)` | **0.12** | + | Consistent presence supports identity |
| `SCREEN_SHARE (during coding)` | **0.10** | + | Behavioural: candidate drives the session |
| `MEETING_EVENT (join as expected)` | **0.05** | + | Weak corroboration |
| `METADATA_NAME match` | **0.03** | + | **Lowest** — trivially spoofable |
| `ANCHOR_MISMATCH` / `FACE_MATCH_FAIL` (present but ≠ anchor) | 0.30 | **−** | Present person is not the anchored candidate → proxy signal |
| `FACE_CHANGED` (embedding drift from anchor) | 0.30 | **−** | Possible candidate switch / proxy |
| `VOICE_CHANGED` | 0.25 | **−** | Possible switch |
| `AV_BINDING_BROKEN` (speaker is off-screen / mismatched lips) | 0.28 | **−** | Someone off-camera is answering → relay proxy |
| `MULTIPLE_FACES / MULTIPLE_SPEAKERS` | 0.10 | **−** | Ambiguity; someone else present |

> **Requirement satisfied:** metadata weight (0.03) is an order of magnitude below the biometric signals. It can never, alone, produce an `IDENTIFIED` verdict.

## 3. Per-Evidence Contribution

A single evidence record contributes:

```
contribution(e) = polarity(e) · w(e) · reliability(e) · magnitude(e) · decay(now − t_e)
```

- `reliability(e) ∈ [0,1]`: observation confidence (e.g., low SNR audio or blurry frame → low reliability). Set by the Evidence Fusion Engine.
- `magnitude(e) ∈ [0,1]`: normalized strength (e.g., cosine match score mapped through a calibration curve; a 0.82 cosine → high magnitude, 0.55 → low).
- `decay(Δt) = exp(−Δt / τ)`: exponential time decay with half-life `τ` (default ~45s, per evidence type). **This is what makes the engine robust to audio drop / camera off** — old evidence fades rather than persisting forever, and its absence lowers *certainty* without flipping the verdict.

## 4. Score Aggregation (log-odds fusion)

We accumulate contributions in **log-odds space**, which naturally bounds to `[0,1]` and models "independent pieces of evidence multiply in probability":

```
logit_p(t) = Σ_e contribution(e)          // sum over live (non-fully-decayed) evidence
score_p(t) = sigmoid( logit_p(t) )        // → (0,1)
```

Then per meeting we select the **argmax participant** as the candidate, but only accept it as `IDENTIFIED` if it also clears absolute and **separation** thresholds (see §6). This prevents crowning a weak leader in an ambiguous meeting.

### Corroboration bonus (fusion > sum of parts)
When independent modalities agree on the same participant within the same window (e.g., `FACE_MATCH` **and** `VOICE_MATCH` both high), we add a small **corroboration bonus** `+β`. Independent agreement is stronger evidence than either signal doubled. Conversely, **cross-modal contradiction** (face says A, voice says B) applies a penalty and can trigger `PROXY_SUSPECTED`.

## 5. State Classification with Hysteresis

Raw score → state via **dual thresholds with hysteresis** (different thresholds to enter vs. leave a state). Hysteresis prevents flicker near a boundary — the #1 source of false positives and noisy UIs.

```
Enter IDENTIFIED : score ≥ 0.85  AND  separation ≥ 0.20  AND  sustained ≥ 8s
Leave IDENTIFIED : score < 0.70   (lower bar to leave than to enter)
UNCERTAIN        : 0.40 ≤ score < 0.85
NO_CANDIDATE     : score < 0.40 for all participants
```

- **separation** = `score(top) − score(second)`. High separation means one clear leader.
- **sustained** = the condition must hold for a dwell time, not a single tick.

## 6. False-Positive Minimization (explicit strategies)

The requirement "false positives should be minimized" is a first-class design constraint, not an afterthought:

1. **Asymmetric thresholds (hysteresis):** hard to *become* IDENTIFIED, easy to *stop* being IDENTIFIED.
2. **Separation requirement:** never declare a winner in a two-way tie.
3. **Corroboration requirement:** an `IDENTIFIED` verdict at high confidence requires ≥2 independent supporting modalities, not one loud signal.
4. **Contradiction penalties + PROXY_SUSPECTED:** conflicting biometrics actively pull confidence down and raise an alert rather than averaging into a falsely-calm middle.
5. **Prefer UNCERTAIN:** when evidence is thin/decayed, the engine emits `UNCERTAIN` with honest reasons, never a confident guess.
6. **Metadata cannot cross the line:** its weight is too small to reach the `IDENTIFIED` threshold alone.

## 7. Handling the Required Edge Cases

| Edge case | Engine behaviour |
|---|---|
| **Proxy interview** | Present face fails match (`FACE_MATCH_FAIL`, −) + voice mismatch → score stays low; state → `PROXY_SUSPECTED`; alert raised. |
| **Candidate switching** | `FACE_CHANGED` / `VOICE_CHANGED` fire negative contributions; separation collapses; state → `CANDIDATE_SWITCHED`; timeline records the moment. |
| **Camera OFF** | Face evidence decays out; voice + behaviour carry the belief; certainty drops but no false flip. State may hold `IDENTIFIED` if voice still corroborates, else `UNCERTAIN`. |
| **Audio drop** | Voice evidence decays; face carries belief. Symmetric to camera-off. |
| **Join / leave** | New participant starts at `INITIALIZING`; leaving freezes state and stops decay updates. |
| **Generic names (iPhone/Guest)** | Metadata contributes ~nothing; identity rests entirely on biometrics/behaviour — exactly the intended design. |
| **Multiple speakers / faces** | Negative ambiguity evidence lowers separation; engine stays `UNCERTAIN` until it resolves. |
| **Temporary network failure** | Kafka buffers; on reconnect, events replay in event-time order; decay is computed from `occurred_at`, so a gap doesn't corrupt the belief — it just widens the decay interval. |

## 8. Statefulness & Recovery

- **Working state** lives in Redis (`conf:{meetingId}:{participantId}`): the current log-odds accumulator, per-evidence-type last contributions, timestamps, current state, dwell timers.
- **Durable checkpoint** in PostgreSQL `confidence.participant_state` on every state transition + periodic interval, including `last_evidence_off` (Kafka offset).
- **Recovery:** on restart/rebalance, load the snapshot, then replay `evidence.events` from `last_evidence_off`. Because contributions are pure functions of `(evidence, occurred_at, now)`, replay is deterministic and idempotent.

## 9. Extensibility (Open/Closed)

Adding a new signal (e.g., typing cadence, ASR-based "answered N questions") requires:
1. A new producer + `*.signals` event.
2. One adapter in the Evidence Fusion Engine mapping it to an `EvidenceRecord` with a weight/polarity.

The Confidence Engine's math is **untouched** — it consumes generic `EvidenceRecord`s. Weights live in **externalized configuration** (a versioned `WeightPolicy`), so tuning is a config change and an A/B-able policy, not a code change. This is the Strategy pattern over scoring policy.

## 10. Worked Example (the spec's example verdict)

```
Participant "John Doe" over 6 minutes:
  FACE_MATCH avg 0.88, reliability 0.9, present 96% of frames
  VOICE_MATCH avg 0.84, dominance 0.91 (answered most questions)
  SCREEN_SHARE active during coding segment
  METADATA name = "John Doe" (weight ~0)
  No FACE_CHANGED / VOICE_CHANGED events

→ log-odds sum high, corroboration bonus applied (face+voice agree)
→ score ≈ 0.94, separation ≈ 0.6, sustained > 8s
→ STATE = IDENTIFIED
→ Contributions ranked → Explanation Engine renders:
     ✓ Answered 91% of interview questions      (DOMINANCE)
     ✓ Shared screen during coding              (SCREEN_SHARE)
     ✓ Face consistently visible                (FACE_PRESENT + FACE_MATCH)
     ✓ Voice remained dominant                  (VOICE_MATCH + DOMINANCE)
```

Matches the target output in the brief, and every line is traceable to weighted evidence.

---

*Previous: [04 — Database Design](./04-database-design.md) · Next: [06 — State Machine](./06-state-machine.md)*
