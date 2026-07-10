"""Singleton model loader (GPU-aware in future; CPU here).

InsightFace `buffalo_l` bundles both the SCRFD face detector and the ArcFace embedder,
so a single `FaceAnalysis.get(bgr)` call yields detection boxes, scores AND 512-d
embeddings — no separate MediaPipe pass. Models are baked into the image at build
(see Dockerfile) so `prepare()` is fast and needs no network at runtime.
"""
from __future__ import annotations

import logging
import os
import threading

# Disable third-party import-time network version checks BEFORE insightface (which
# imports albumentations) is loaded. Without this, a slow/limited outbound DNS makes
# the check hang and model loading never completes. Belt-and-suspenders to the same
# vars set in docker-compose.yml, so a host run is protected too.
os.environ.setdefault("NO_ALBUMENTATIONS_UPDATE", "1")
os.environ.setdefault("HF_HUB_OFFLINE", "1")

import numpy as np

from app.infrastructure.config import settings

log = logging.getLogger(__name__)

_lock = threading.Lock()
_app = None  # insightface.app.FaceAnalysis


def load() -> None:
    """Eagerly load models (called once at startup so /health reflects readiness)."""
    global _app
    if _app is not None:
        return
    with _lock:
        if _app is not None:
            return
        from insightface.app import FaceAnalysis

        # Only detection + recognition are needed (boxes + 512-d embedding); skipping
        # genderage/landmark models makes startup faster and lighter.
        app = FaceAnalysis(
            name="buffalo_l",
            allowed_modules=["detection", "recognition"],
            providers=["CPUExecutionProvider"],
        )
        app.prepare(ctx_id=-1, det_size=(settings.det_size, settings.det_size))
        _app = app
        log.info("InsightFace buffalo_l loaded (det+rec, det_size=%d)", settings.det_size)


def is_ready() -> bool:
    return _app is not None


def detect_faces(bgr: np.ndarray) -> list:
    """Run detection + embedding on a BGR frame; returns InsightFace Face objects."""
    if _app is None:
        load()
    return _app.get(bgr)
