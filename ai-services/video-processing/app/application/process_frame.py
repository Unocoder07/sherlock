"""Use case: one MediaFrameMeta -> fetch -> detect -> embed -> publish video.signals.

Stateless per frame except for the per-participant lip-activity window (motion needs
the previous frame). Wiring only — the math lives in domain/, IO in adapters/.
"""
from __future__ import annotations

import logging

import cv2
import numpy as np
from sherlock.media.v1 import media_pb2

from app.adapters.kafka_producer import VideoSignalProducer
from app.adapters.object_store import ObjectStore
from app.domain import signal_builder
from app.domain.detectors import embedder, face_detector
from app.domain.lip_activity import LipActivityTracker
from app.domain.quality import composite_quality
from app.infrastructure import model_registry
from app.infrastructure.config import settings

log = logging.getLogger(__name__)


def _mouth_roi_gray(bgr: np.ndarray, bbox) -> np.ndarray:
    """Lower-central slice of the face box, grayscaled — where the mouth lives."""
    h, w = bgr.shape[:2]
    x1, y1, x2, y2 = (int(round(v)) for v in bbox)
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(w, x2), min(h, y2)
    if x2 <= x1 or y2 <= y1:
        return np.zeros((1, 1), dtype=np.uint8)
    fh, fw = y2 - y1, x2 - x1
    my1 = y1 + int(fh * 0.60)
    my2 = y1 + int(fh * 0.95)
    mx1 = x1 + int(fw * 0.25)
    mx2 = x1 + int(fw * 0.75)
    roi = bgr[my1:my2, mx1:mx2]
    if roi.size == 0:
        return np.zeros((1, 1), dtype=np.uint8)
    return cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)


class FrameProcessor:
    def __init__(self, store: ObjectStore, producer: VideoSignalProducer) -> None:
        self._store = store
        self._producer = producer
        self._lip = LipActivityTracker(window=settings.lip_window, gain=settings.lip_gain)

    def process(self, meta: media_pb2.MediaFrameMeta, occurred_at_ms: int) -> int:
        """Process one frame; returns the number of signals published."""
        if meta.kind != media_pb2.MEDIA_KIND_VIDEO_FRAME:
            return 0

        raw = self._store.get_bytes(meta.uri)
        bgr = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
        if bgr is None:
            log.warning("undecodable frame %s", meta.uri)
            return 0

        frame_area = float(bgr.shape[0] * bgr.shape[1])
        faces = model_registry.detect_faces(bgr)
        face_count = face_detector.count_confident(faces, settings.det_threshold)

        if face_count <= 0:
            self._lip.reset(meta.participant_id)
            det = signal_builder.Detection(0, 0.0, 0.0, 0.0, "")
        else:
            face = face_detector.largest_face(faces)
            quality = composite_quality(
                _crop(bgr, face.bbox), face_detector.bbox_area(face.bbox), frame_area
            )
            lip = self._lip.update(meta.participant_id, _mouth_roi_gray(bgr, face.bbox))
            embedding = embedder.extract_embedding(face)
            ref = self._store.put_embedding(
                meta.meeting_id, meta.participant_id, meta.sequence, embedding
            )
            det = signal_builder.Detection(
                face_count=face_count,
                detection_conf=float(getattr(face, "det_score", 0.0)),
                quality=quality,
                lip_activity=lip,
                embedding_ref=ref,
            )

        signals = signal_builder.build_signals(det, meta.captured_at_ms)
        for s in signals:
            self._producer.publish(meta.meeting_id, meta.participant_id, occurred_at_ms, s)
        return len(signals)


def _crop(bgr: np.ndarray, bbox) -> np.ndarray:
    h, w = bgr.shape[:2]
    x1, y1, x2, y2 = (int(round(v)) for v in bbox)
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(w, x2), min(h, y2)
    if x2 <= x1 or y2 <= y1:
        return bgr
    return bgr[y1:y2, x1:x2]
