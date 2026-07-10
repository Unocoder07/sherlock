#!/usr/bin/env python3
"""Host webcam → Sherlock media path (M5 ingestion).

Grabs frames from the local webcam, writes each JPEG to the MinIO `sherlock-media`
bucket, and publishes a `media.frames.meta` event (EventEnvelope-wrapped) to Kafka.
The Video Processing Service consumes those, runs InsightFace, and emits
`video.signals`. Runs on the HOST (Docker-on-Windows can't reach the camera), so it
targets the host-facing listeners: Kafka localhost:29092, MinIO localhost:9000.

    python capture.py --meeting m5-live --participant p-me --seconds 15

Deps:  pip install -r requirements.txt
Stubs: this script adds contracts/gen-python to sys.path automatically (run
       `cd contracts && buf generate` once first).
"""
from __future__ import annotations

import argparse
import io
import sys
import time
import uuid
from pathlib import Path

# ── make the generated protobuf stubs importable (repo_root/contracts/gen-python) ──
_REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(_REPO_ROOT / "contracts" / "gen-python"))

import cv2  # noqa: E402
from confluent_kafka import Producer  # noqa: E402
from minio import Minio  # noqa: E402
from sherlock.common.v1 import envelope_pb2  # noqa: E402
from sherlock.media.v1 import media_pb2  # noqa: E402
from sherlock.meeting.v1 import meeting_events_pb2  # noqa: E402

PRODUCER = "webcam-capture@0.1.0"
BUCKET = "sherlock-media"
TOPIC_FRAMES = "media.frames.meta"
TOPIC_MEETING = "meeting.events"


def _now_ms() -> int:
    return int(time.time() * 1000)


def wrap(payload, meeting_id: str, participant_id: str, occurred_at_ms: int) -> bytes:
    env = envelope_pb2.EventEnvelope(
        event_id=str(uuid.uuid4()),
        event_type=payload.DESCRIPTOR.full_name,
        schema_version=1,
        meeting_id=meeting_id,
        participant_id=participant_id,
        occurred_at_ms=occurred_at_ms,
        emitted_at_ms=_now_ms(),
        producer=PRODUCER,
        payload=payload.SerializeToString(),
    )
    return env.SerializeToString()


def main() -> int:
    ap = argparse.ArgumentParser(description="Sherlock webcam capture (M5)")
    ap.add_argument("--meeting", required=True)
    ap.add_argument("--participant", default="p-me")
    ap.add_argument("--fps", type=float, default=3.0)
    ap.add_argument("--seconds", type=float, default=15.0)
    ap.add_argument("--bootstrap", default="localhost:29092")
    ap.add_argument("--minio", default="localhost:9000")
    ap.add_argument("--camera", type=int, default=0, help="cv2 VideoCapture index")
    ap.add_argument("--jpeg-quality", type=int, default=80)
    args = ap.parse_args()

    print(f"▶ meeting={args.meeting} participant={args.participant} "
          f"fps={args.fps} seconds={args.seconds} kafka={args.bootstrap} minio={args.minio}")

    minio = Minio(args.minio, access_key="sherlock", secret_key="sherlock123", secure=False)
    if not minio.bucket_exists(BUCKET):
        minio.make_bucket(BUCKET)

    producer = Producer({"bootstrap.servers": args.bootstrap, "acks": "all",
                         "enable.idempotence": True})

    # Announce the participant so downstream has a roster entry (mirrors the simulator).
    joined = meeting_events_pb2.ParticipantJoined(
        meeting_id=args.meeting, participant_id=args.participant,
        display_name="Webcam", camera_on=True)
    producer.produce(TOPIC_MEETING, key=args.meeting.encode(),
                     value=wrap(joined, args.meeting, args.participant, _now_ms()))
    producer.flush(5)

    cap = cv2.VideoCapture(args.camera)
    if not cap.isOpened():
        print(f"✖ could not open camera index {args.camera}", file=sys.stderr)
        return 2

    interval = 1.0 / max(0.1, args.fps)
    deadline = time.time() + args.seconds
    seq = 0
    sent = 0
    try:
        while time.time() < deadline:
            ok, frame = cap.read()
            if not ok:
                print("… dropped a frame (camera returned no image)")
                time.sleep(interval)
                continue

            ok, buf = cv2.imencode(".jpg", frame,
                                   [int(cv2.IMWRITE_JPEG_QUALITY), args.jpeg_quality])
            if not ok:
                continue
            data = buf.tobytes()
            key = f"{args.meeting}/{args.participant}/{seq}.jpg"
            minio.put_object(BUCKET, key, io.BytesIO(data), length=len(data),
                             content_type="image/jpeg")

            h, w = frame.shape[:2]
            captured = _now_ms()
            meta = media_pb2.MediaFrameMeta(
                meeting_id=args.meeting, participant_id=args.participant,
                kind=media_pb2.MEDIA_KIND_VIDEO_FRAME, uri=f"s3://{BUCKET}/{key}",
                captured_at_ms=captured, sequence=seq, width=w, height=h)
            producer.produce(TOPIC_FRAMES, key=args.meeting.encode(),
                             value=wrap(meta, args.meeting, args.participant, captured))
            producer.poll(0)

            seq += 1
            sent += 1
            if sent % 5 == 0:
                print(f"  … sent {sent} frames")
            time.sleep(interval)
    finally:
        cap.release()
        producer.flush(10)
    print(f"✔ done — published {sent} frames to {TOPIC_FRAMES}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
