# Video Processing Service (M5)

Turns real webcam frames into **video signals**. Consumes `media.frames.meta`, fetches
the frame JPEG from MinIO, runs face detection + embedding, and publishes `video.signals`.

```
media.frames.meta ─▶ [ fetch frame ─▶ detect ─▶ embed ─▶ quality/lip ] ─▶ video.signals
   (from webcam                InsightFace buffalo_l        (embedding stored in
    capture client)            + lower-face motion           sherlock-embeddings,
                                                              ref on the wire)
```

Stateless per frame except a small per-participant lip-activity window (motion needs the
previous frame). Clean-architecture layout (`app/domain` pure, `app/adapters` I/O,
`app/application` orchestration) per `docs/architecture/09-folder-structures.md`.

## What it emits (per frame)

| Condition | `video.signals` |
|---|---|
| ≥1 face | `FACE_PRESENT` with `detection_conf`, `quality`, `lip_activity`, `embedding_ref` |
| >1 face | additionally `MULTIPLE_FACES` |
| no face | `FACE_ABSENT` |

`embedding_ref` = `s3://sherlock-embeddings/<meeting>/<participant>/<seq>.npy` — a real
512-d L2-normalized ArcFace vector. (Consumed for real only by the **M6.5** Identity
Anchor; the M4/M5 mock anchor is embedding-agnostic.)

## Design notes / deviations

- **InsightFace `buffalo_l` only** — it bundles the SCRFD detector *and* the ArcFace
  embedder, so one `get()` call yields boxes, scores and embeddings. This deliberately
  replaces the doc's separate *MediaPipe detection* step to avoid a second model and a
  protobuf-version clash (MediaPipe pins an incompatible protobuf).
- **Lip-activity is landmark-free**: frame-to-frame motion in the lower-central face
  region, smoothed over a short window. It rises when the mouth moves — enough for the
  A/V-binding signal — without depending on a face-mesh model. Swap in true MediaPipe
  FaceMesh MAR later if higher fidelity is needed.

## Run

Built and wired via the root `docker-compose.yml` (service `video-processing`, port 8098).
Prerequisite: generate Python stubs once — `cd contracts && buf generate` (or `make
contracts`) — the image COPYs `contracts/gen-python`.

```
docker compose build video-processing        # build alone (large: ML deps + baked models)
docker compose up -d video-processing
curl -s http://127.0.0.1:8098/health          # {"status":"ok","models_loaded":true}
```

Then drive frames with the host capture client (`tools/webcam-capture/`).

## Tests

Pure-domain units (quality, lip-activity, signal-builder) need only numpy:

```
docker run --rm -v "$PWD:/w" -w /w python:3.11-slim \
  bash -c "pip install -q numpy pytest && python -m pytest tests/ -q"
```

## Config (env)

`KAFKA_BOOTSTRAP_SERVERS` (kafka:9092), `MINIO_ENDPOINT` (http://minio:9000),
`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`, `BUCKET_MEDIA`, `BUCKET_EMBEDDINGS`,
`DET_THRESHOLD`, `LIP_WINDOW`, `LIP_GAIN`, `SERVER_PORT` (8098).
