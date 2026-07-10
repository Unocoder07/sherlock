# 07 — Sequence Diagrams (PlantUML)

Render with any PlantUML tool. These cover the flows that matter most for review.

## 1. Meeting Setup — No Reference Upload (Control Plane)

The system starts with **zero biometric knowledge**. Setup only creates the meeting; there is **no candidate photo/voice upload**. A reference is *optional* and, if used, is captured **live at join** (an ID-verification step), never pre-supplied by the interviewer.

```plantuml
@startuml
actor Interviewer
participant "Next.js UI" as UI
participant "REST API" as API
participant "Meeting Service" as MS
database "PostgreSQL" as PG
participant "Kafka" as K

Interviewer -> UI : Create meeting (title, schedule, expected name = metadata only)
UI -> API : POST /meetings
API -> MS : createMeeting(cmd)
MS -> PG : persist Meeting(SCHEDULED)  // NO biometric reference stored
MS -> K : publish meeting.events (MEETING_CREATED)
API --> UI : 201 Created

note over MS : Reference-anchored mode is OPTIONAL and happens LIVE at join\n(see diagram 1b), not here. Default = self-anchored (zero-knowledge).
@enduml
```

## 1b. Cold-Start Identity Anchoring (the "0 info → candidate known" flow)

This is the flow that answers *"where does the reference come from?"* — Sherlock **builds it from the interview itself**.

```plantuml
@startuml
participant "Video Svc" as V
participant "Audio Svc" as A
participant "Kafka" as K
participant "Identity Anchor Svc" as AN
database "Redis" as R
participant "Evidence Fusion" as EF
participant "Confidence Engine" as CE
actor Interviewer

== OBSERVING (zero knowledge) ==
V -> K : video.signals (face embeddings, lip-activity) for all participants
A -> K : audio.signals (voice embeddings, VAD) for all participants
K -> AN : consume embeddings
AN -> R : buffer per-participant embeddings; NO anchor yet
AN -> CE : (low-reliability signals) -> state stays OBSERVING/UNCERTAIN

== ANCHORING (build self-reference) ==
AN -> AN : online-cluster embeddings; find dominant, consistent participant
AN -> AN : A/V BINDING — correlate lip motion with active voice\n(confirms the visible face is the speaker)
AN -> R : write provisional anchor (face centroid + voice centroid + binding)
AN -> K : identity.anchor (ANCHOR_FORMING)

== LOCKED (candidate now known to the system) ==
AN -> AN : anchor stable (tight cluster, sustained dominance, strong binding)
AN -> K : identity.anchor (ANCHOR_LOCKED)
AN -> K : consistency signals (FACE_CONSISTENT_WITH_ANCHOR, VOICE_..., AV_BINDING)
K -> EF : normalize to evidence.events
K -> CE : score rises -> IDENTIFIED (anchor locked + thresholds met)
CE -> Interviewer : verdict + reasons ("same person present & answering throughout")
@enduml
```

## 2. Live Signal → Verdict (Data Plane, the core loop)

```plantuml
@startuml
participant "Ingestion" as IN
participant "Object Store" as OBJ
participant "Kafka" as K
participant "Video Svc" as V
participant "Audio Svc" as A
participant "Evidence Fusion" as EF
participant "Confidence Engine" as CE
database "Redis" as R
database "PostgreSQL" as PG
participant "Explanation" as EX
participant "Timeline" as TL
participant "Notification" as NO
participant "WS Gateway" as WS
actor Interviewer

IN -> OBJ : write frame + audio chunk
IN -> K : media.frames.meta (ref, ts, participantId)

K -> V : consume media meta
V -> OBJ : fetch frame bytes
V -> V : MediaPipe detect -> InsightFace embed (+ lip-activity)
V -> K : video.signals (face embedding, QUALITY, faceCount)

K -> A : consume media meta
A -> OBJ : fetch audio bytes
A -> A : Silero VAD -> pyannote embed + dominance
A -> K : audio.signals (voice embedding, VAD, DOMINANCE{0.91})

participant "Identity Anchor Svc" as AN
K -> AN : consume video.signals + audio.signals
AN -> AN : compare embeddings to LOCKED anchor + A/V binding
AN -> K : FACE_CONSISTENT_WITH_ANCHOR{0.88}, VOICE_CONSISTENT_WITH_ANCHOR{0.84}, AV_BINDING{0.9}

K -> EF : consume video.signals + audio.signals + anchor consistency signals
EF -> EF : normalize -> EvidenceRecord(weight, reliability, polarity)
EF -> EF : cross-modal corroboration check
EF -> K : evidence.events

K -> CE : consume evidence.events (keyed meetingId+participantId)
CE -> R : load accumulator
CE -> CE : apply decay + contribution + corroboration bonus
CE -> CE : classify state (hysteresis)
CE -> R : write accumulator + state
CE -> PG : checkpoint participant_state (on transition)
CE -> K : confidence.updates (score, state, contributions)

K -> EX : consume confidence.updates
EX -> EX : render top reasons from contributions
EX -> K : enriched verdict

K -> TL : consume confidence.updates
TL -> PG : append timeline_entry
TL -> K : timeline.events

K -> NO : consume confidence.updates (transition of interest)
NO -> NO : rule match? throttle/dedupe
NO -> Interviewer : alert (if CRITICAL)

K -> WS : consume enriched verdict + timeline.events
WS -> Interviewer : push live verdict + timeline (WSS)
@enduml
```

## 3. Proxy Detection Flow (two variants — both work with NO reference)

```plantuml
@startuml
participant "Video/Audio Svc" as V
participant "Identity Anchor Svc" as AN
participant "Kafka" as K
participant "Evidence Fusion" as EF
participant "Confidence Engine" as CE
participant "Notification" as NO
actor Interviewer

== Variant A: person on camera drifts away from the anchor (switch) ==
V -> K : video.signals (new face embedding)
K -> AN : compare to LOCKED anchor
AN -> AN : embedding distance high -> drift/mismatch
AN -> K : ANCHOR_MISMATCH / FACE_CHANGED (polarity -1)

== Variant B: relay — visible face is NOT the one answering ==
V -> K : video.signals (face + lip-activity) + audio.signals (voice + VAD)
K -> AN : correlate lips with active voice
AN -> AN : lip motion does NOT align with the dominant voice
AN -> K : AV_BINDING_BROKEN (polarity -1) — someone off-screen is answering

K -> EF : consume mismatch signals
EF -> K : evidence.events (negative, weight ~0.30)
K -> CE : consume
CE -> CE : negative contribution dominates; state -> PROXY_SUSPECTED
CE -> K : confidence.updates (state=PROXY_SUSPECTED, reasons)
K -> NO : consume transition
NO -> Interviewer : CRITICAL alert "Person answering does not match the anchored candidate"
@enduml
```

## 4. Camera-Off Graceful Degradation

```plantuml
@startuml
participant "Video Svc" as V
participant "Audio Svc" as A
participant "Confidence Engine" as CE
participant "WS Gateway" as WS
actor Interviewer

note over V : Camera turned OFF -> no face signals produced
V -x CE : (no video evidence)
note over CE : face evidence decays exp(-dt/tau)
A -> CE : audio.signals continue (VOICE_MATCH 0.85, DOMINANCE high)
CE -> CE : certainty drops but voice corroborates -> holds IDENTIFIED\n(or UNCERTAIN if voice also weak)
CE -> WS : confidence.updates (score slightly lower, reason: "camera off, voice consistent")
WS -> Interviewer : verdict stays stable, no false flip
@enduml
```

## 5. Client Subscription & Live Push (WebSocket)

```plantuml
@startuml
actor Interviewer
participant "Next.js UI" as UI
participant "WS Gateway" as WS
participant "Redis" as R
participant "Kafka" as K

Interviewer -> UI : open meeting dashboard
UI -> WS : CONNECT (JWT) + SUBSCRIBE /topic/meetings/{id}/verdict
WS -> WS : validate JWT, authorize meeting access
WS -> R : register session in ws:subs:{meetingId}
WS -> UI : send last-known verdict snapshot (immediate paint)
loop live
  K -> WS : confidence.updates / timeline.events
  WS -> UI : push frame (verdict, timeline delta)
end
Interviewer -> UI : close tab
UI -> WS : DISCONNECT
WS -> R : remove session from ws:subs:{meetingId}
@enduml
```

---

*Previous: [06 — State Machine](./06-state-machine.md) · Next: [08 — API Gateway & Contracts](./08-api-gateway.md)*
