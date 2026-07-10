"""Pure signal construction: detector outcome -> framework-free VideoSignal data.

Kept proto-free (returns dataclasses) so the mapping rules are unit-testable without
protobuf or Kafka. The producer adapter maps `VideoSignalData` -> the proto message.
Mirrors the fields the mock simulator set (SimulatorMain.facePresent/faceAbsent).
"""
from __future__ import annotations

from dataclasses import dataclass


# VideoSignalType enum names (must match sherlock.signals.v1.VideoSignalType).
FACE_PRESENT = "VIDEO_SIGNAL_TYPE_FACE_PRESENT"
FACE_ABSENT = "VIDEO_SIGNAL_TYPE_FACE_ABSENT"
MULTIPLE_FACES = "VIDEO_SIGNAL_TYPE_MULTIPLE_FACES"


@dataclass(frozen=True)
class VideoSignalData:
    type: str
    face_count: int
    detection_conf: float
    quality: float
    embedding_ref: str
    lip_activity: float
    window_start_ms: int
    window_end_ms: int


@dataclass(frozen=True)
class Detection:
    """What the detector saw in one frame (already reduced to the dominant face)."""
    face_count: int
    detection_conf: float
    quality: float
    lip_activity: float
    embedding_ref: str


def build_signals(det: Detection, captured_at_ms: int) -> list[VideoSignalData]:
    """Map a frame's detection into 0..2 VideoSignals.

    - No face                -> FACE_ABSENT
    - >= 1 face              -> FACE_PRESENT (carries embedding_ref + lip + quality)
    - > 1 face               -> additionally MULTIPLE_FACES
    """
    if det.face_count <= 0:
        return [
            VideoSignalData(
                type=FACE_ABSENT,
                face_count=0,
                detection_conf=0.0,
                quality=0.0,
                embedding_ref="",
                lip_activity=0.0,
                window_start_ms=captured_at_ms,
                window_end_ms=captured_at_ms,
            )
        ]

    signals = [
        VideoSignalData(
            type=FACE_PRESENT,
            face_count=det.face_count,
            detection_conf=round(det.detection_conf, 4),
            quality=round(det.quality, 4),
            embedding_ref=det.embedding_ref,
            lip_activity=round(det.lip_activity, 4),
            window_start_ms=captured_at_ms,
            window_end_ms=captured_at_ms,
        )
    ]
    if det.face_count > 1:
        signals.append(
            VideoSignalData(
                type=MULTIPLE_FACES,
                face_count=det.face_count,
                detection_conf=round(det.detection_conf, 4),
                quality=round(det.quality, 4),
                embedding_ref="",
                lip_activity=0.0,
                window_start_ms=captured_at_ms,
                window_end_ms=captured_at_ms,
            )
        )
    return signals
