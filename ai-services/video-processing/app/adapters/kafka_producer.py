"""Publishes VideoSignals to the `video.signals` topic (envelope-wrapped)."""
from __future__ import annotations

import logging

from confluent_kafka import Producer
from sherlock.signals.v1 import video_signals_pb2

from app import constants
from app.domain.signal_builder import VideoSignalData
from app.infrastructure import envelope
from app.infrastructure.config import settings

log = logging.getLogger(__name__)

# String enum name -> proto enum value.
_TYPE = {
    "VIDEO_SIGNAL_TYPE_FACE_PRESENT": video_signals_pb2.VIDEO_SIGNAL_TYPE_FACE_PRESENT,
    "VIDEO_SIGNAL_TYPE_FACE_ABSENT": video_signals_pb2.VIDEO_SIGNAL_TYPE_FACE_ABSENT,
    "VIDEO_SIGNAL_TYPE_MULTIPLE_FACES": video_signals_pb2.VIDEO_SIGNAL_TYPE_MULTIPLE_FACES,
}


class VideoSignalProducer:
    def __init__(self) -> None:
        self._p = Producer({
            "bootstrap.servers": settings.kafka_bootstrap_servers,
            "acks": "all",
            "enable.idempotence": True,
        })

    def publish(self, meeting_id: str, participant_id: str, occurred_at_ms: int,
                signal: VideoSignalData) -> None:
        payload = video_signals_pb2.VideoSignal(
            meeting_id=meeting_id,
            participant_id=participant_id,
            type=_TYPE[signal.type],
            face_count=signal.face_count,
            detection_conf=signal.detection_conf,
            quality=signal.quality,
            embedding_ref=signal.embedding_ref,
            lip_activity=signal.lip_activity,
            window_start_ms=signal.window_start_ms,
            window_end_ms=signal.window_end_ms,
        )
        value = envelope.wrap(
            payload,
            producer=constants.PRODUCER,
            meeting_id=meeting_id,
            participant_id=participant_id,
            occurred_at_ms=occurred_at_ms,
        )
        self._p.produce(constants.TOPIC_VIDEO_SIGNALS, key=meeting_id.encode(), value=value)
        self._p.poll(0)

    def flush(self, timeout: float = 5.0) -> None:
        self._p.flush(timeout)
