# Sherlock Contracts

**Single source of truth for every event on the Kafka bus.** All producers and
consumers — Java and Python — generate their types from these `.proto` files so
the two languages can never drift.

## Layout

```
contracts/
├── proto/sherlock/
│   ├── common/v1/envelope.proto        # EventEnvelope (wraps every message)
│   ├── meeting/v1/meeting_events.proto # meeting.events
│   ├── media/v1/media.proto            # media.frames.meta (refs, never bytes)
│   ├── signals/v1/video_signals.proto  # video.signals
│   ├── signals/v1/audio_signals.proto  # audio.signals
│   ├── anchor/v1/identity_anchor.proto # identity.anchor (self-built reference)
│   ├── evidence/v1/evidence.proto      # evidence.events (normalized)
│   └── confidence/v1/confidence.proto  # confidence.updates (verdicts)
├── buf.yaml                            # module + lint/breaking config
└── buf.gen.yaml                        # codegen (Java + Python via remote plugins)
```

## The envelope pattern

Every Kafka message is an `EventEnvelope`. The concrete payload (e.g. `VideoSignal`)
is serialized into `envelope.payload` (bytes) and identified by `envelope.event_type`.
This gives one uniform, versioned wrapper across all topics while each event keeps its
own schema. Partition key is always `meeting_id`.

## Generating stubs

Requires [buf](https://buf.build/docs/installation) (`buf` on PATH).

```bash
cd contracts
buf lint         # validate schemas
buf generate     # -> gen-java/  and  gen-python/  (git-ignored)
```

- **Java** consumes `gen-java/` via a Gradle source set (wired up in M1).
- **Python** consumes `gen-python/` as an importable package (wired up in M5/M6).

> Generated code is intentionally **not** committed (`.gitignore`). CI regenerates it,
> guaranteeing it always matches the `.proto` source.

## Versioning rules

- Packages are versioned (`.v1`). Breaking changes go to a new version package.
- `buf breaking` (in CI) blocks accidental incompatible edits against the main branch.
- Bump `EventEnvelope.schema_version` when a payload's meaning changes.
