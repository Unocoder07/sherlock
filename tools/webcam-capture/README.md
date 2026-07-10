# Webcam Capture (M5 ingestion)

Host-run client that feeds the **real** media path: webcam → MinIO → `media.frames.meta`.
The Video Processing Service then produces `video.signals`. This runs on the host because
Docker Desktop on Windows cannot access the camera.

## Prerequisites
1. Stack up: `docker compose up -d` (needs Kafka + MinIO + `video-processing`).
2. Generated stubs present: `cd contracts && buf generate` (the script adds
   `contracts/gen-python` to `sys.path` automatically).
3. `pip install -r requirements.txt` (a venv is recommended).

## Run
```
python capture.py --meeting m5-live --participant p-me --seconds 15
```
Options: `--fps` (default 3), `--bootstrap` (default `localhost:29092`),
`--minio` (default `localhost:9000`), `--camera` (cv2 index, default 0),
`--jpeg-quality` (default 80).

On start it emits a `ParticipantJoined` to `meeting.events`, then streams JPEG frames to
`sherlock-media` and publishes one `media.frames.meta` per frame.

## Verify it's flowing
- Kafka UI (`http://localhost:8080`) → topic `video.signals` should show `FACE_PRESENT`
  messages with a non-empty `embedding_ref`, real `detection_conf`/`quality`, and a
  `lip_activity` that rises when you move your mouth. Cover the camera → `FACE_ABSENT`.
- MinIO console (`http://localhost:9001`, sherlock/sherlock123) → `sherlock-media` (frames)
  and `sherlock-embeddings` (512-float `.npy` vectors) fill up.
