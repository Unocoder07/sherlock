# 08 — API Gateway & Contracts

## 1. Edge Layer Composition

The "gateway" is a thin, secure edge composed of two Spring Boot surfaces plus cross-cutting concerns:

```
                    ┌──────────────────────────────────────────────┐
   Client  ──HTTPS─►│  API Gateway (Spring Cloud Gateway / reverse  │
   Client  ──WSS───►│  proxy)                                       │
                    │   • TLS termination                           │
                    │   • JWT auth + RBAC (Spring Security)         │
                    │   • Rate limiting (Redis token bucket)        │
                    │   • Request/trace id injection (OTel)         │
                    │   • CORS, request validation                  │
                    └───────┬──────────────────────┬────────────────┘
                            ▼                       ▼
                   ┌────────────────┐      ┌────────────────────┐
                   │  REST API      │      │  WebSocket Gateway │
                   │  (commands/    │      │  (STOMP over WS)   │
                   │   queries)     │      │                    │
                   └────────────────┘      └────────────────────┘
```

> **Why a dedicated gateway?** Centralizes auth, TLS, rate limiting, and observability so individual services don't re-implement them (DRY + security consistency). It is *not* a business-logic layer — it routes and protects.

## 2. AuthN / AuthZ

- **AuthN:** OAuth2 / JWT bearer tokens. Roles: `INTERVIEWER`, `ADMIN`, `SYSTEM` (service-to-service via mTLS or client-credentials).
- **AuthZ:** method-level RBAC (Spring Security `@PreAuthorize`). Meeting-scoped access checks (an interviewer sees only meetings they're assigned to).
- **WS handshake:** JWT passed in the CONNECT frame; validated before any subscription; per-meeting authorization enforced on SUBSCRIBE.

## 3. REST Contract (v1)

Base path `/api/v1`. All responses use a standard envelope and RFC-7807 problem+json for errors.

### Meetings
| Method | Path | Purpose |
|---|---|---|
| `POST` | `/meetings` | Create a meeting |
| `GET` | `/meetings/{id}` | Get meeting + current verdict summary |
| `POST` | `/meetings/{id}/expected-candidate` | Enrol expected candidate + reference biometrics (multipart) |
| `POST` | `/meetings/{id}/start` | Begin monitoring |
| `POST` | `/meetings/{id}/stop` | End monitoring |
| `GET` | `/meetings/{id}/participants` | List participants + per-participant state |

### Verdict & Timeline
| Method | Path | Purpose |
|---|---|---|
| `GET` | `/meetings/{id}/verdict` | Current verdict (candidate, score, state, reasons) |
| `GET` | `/meetings/{id}/verdict/history` | Verdict changes over time (paginated) |
| `GET` | `/meetings/{id}/timeline` | Timeline entries (paginated, filterable by type) |
| `GET` | `/meetings/{id}/participants/{pid}/evidence` | Evidence audit for a participant (paginated) |

### Example: `GET /meetings/{id}/verdict` (200)
```json
{
  "meetingId": "3f2a...",
  "candidate": { "participantId": "a91...", "displayName": "John Doe" },
  "confidence": 0.94,
  "state": "IDENTIFIED",
  "updatedAt": "2026-07-08T10:41:12Z",
  "reasons": [
    { "text": "Answered 91% of interview questions", "type": "DOMINANCE", "contribution": 0.21 },
    { "text": "Shared screen during coding", "type": "SCREEN_SHARE", "contribution": 0.10 },
    { "text": "Face consistently visible", "type": "FACE_MATCH", "contribution": 0.29 },
    { "text": "Voice remained dominant", "type": "VOICE_MATCH", "contribution": 0.24 }
  ],
  "concerns": []
}
```

### Example: PROXY case (`concerns` populated)
```json
{
  "meetingId": "3f2a...",
  "candidate": { "participantId": "b47...", "displayName": "Guest" },
  "confidence": 0.28,
  "state": "PROXY_SUSPECTED",
  "reasons": [ { "text": "Voice partially matches", "type": "VOICE_MATCH", "contribution": 0.09 } ],
  "concerns": [
    { "text": "Visible face does not match enrolled candidate", "type": "FACE_MATCH_FAIL", "severity": "CRITICAL" }
  ]
}
```

## 4. WebSocket Contract (STOMP over WSS)

| Destination | Direction | Payload |
|---|---|---|
| `/app/meetings/{id}/subscribe` | client → server | subscribe intent (auth-checked) |
| `/topic/meetings/{id}/verdict` | server → client | `ConfidenceUpdate` (verdict + reasons + concerns) |
| `/topic/meetings/{id}/timeline` | server → client | `TimelineEntry` deltas |
| `/topic/meetings/{id}/alerts` | server → client | critical notifications (proxy/switch) |
| `/user/queue/errors` | server → client | per-session error frames |

On subscribe, the gateway **immediately sends the last-known snapshot** (from Redis) so the UI paints without waiting for the next event.

## 5. API Standards

- **Versioning:** URI-based (`/api/v1`) for REST; schema-version field for events.
- **Idempotency:** mutating POSTs accept an `Idempotency-Key` header.
- **Pagination:** cursor-based (`?cursor=&limit=`) for timelines/evidence (large, append-only).
- **Errors:** RFC 7807 (`application/problem+json`) with a stable `type`, `title`, `detail`, `traceId`.
- **Rate limiting:** Redis token-bucket per principal + per meeting; 429 with `Retry-After`.
- **Observability:** every request carries a `traceparent`; propagated into Kafka event envelopes end-to-end.

## 6. DTO / Contract Discipline

- REST DTOs are **separate from domain entities** (DTO pattern) — the API contract evolves independently of the schema; entities never leak out of the service.
- Event schemas live in a **shared, versioned schema module** (`sherlock-contracts`) consumed by both Java (generated classes) and Python (generated stubs). Single source of truth prevents drift between producers and consumers.

---

*Previous: [07 — Sequence Diagrams](./07-sequence-diagrams.md) · Next: [09 — Folder Structures](./09-folder-structures.md)*
