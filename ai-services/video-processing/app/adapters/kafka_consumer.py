"""Consumes `media.frames.meta`, runs the frame processor, commits manually.

Runs its poll loop on a background thread started at app startup. Dedupes on the
envelope `event_id` (bounded cache) per the contract's idempotency requirement.
"""
from __future__ import annotations

import logging
import threading
from collections import OrderedDict

from confluent_kafka import Consumer
from sherlock.media.v1 import media_pb2

from app import constants
from app.application.process_frame import FrameProcessor
from app.infrastructure import envelope
from app.infrastructure.config import settings

log = logging.getLogger(__name__)

_DEDUPE_MAX = 5000


class FrameConsumer:
    def __init__(self, processor: FrameProcessor) -> None:
        self._processor = processor
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._seen: OrderedDict[str, None] = OrderedDict()
        self.frames_processed = 0
        self.signals_published = 0

    def start(self) -> None:
        self._thread = threading.Thread(target=self._run, name="frame-consumer", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=10)

    def _dedupe(self, event_id: str) -> bool:
        """True if this event_id was already handled."""
        if event_id in self._seen:
            return True
        self._seen[event_id] = None
        if len(self._seen) > _DEDUPE_MAX:
            self._seen.popitem(last=False)
        return False

    def _run(self) -> None:
        consumer = Consumer({
            "bootstrap.servers": settings.kafka_bootstrap_servers,
            "group.id": settings.consumer_group,
            "auto.offset.reset": "earliest",
            "enable.auto.commit": False,
        })
        consumer.subscribe([constants.TOPIC_MEDIA_FRAMES])
        log.info("consuming %s @ %s", constants.TOPIC_MEDIA_FRAMES, settings.kafka_bootstrap_servers)
        try:
            while not self._stop.is_set():
                msg = consumer.poll(1.0)
                if msg is None:
                    continue
                if msg.error():
                    log.warning("consumer error: %s", msg.error())
                    continue
                try:
                    env = envelope.parse(msg.value())
                    if env.event_type != constants.TYPE_MEDIA_FRAME_META:
                        consumer.commit(msg, asynchronous=False)
                        continue
                    if self._dedupe(env.event_id):
                        consumer.commit(msg, asynchronous=False)
                        continue
                    meta = media_pb2.MediaFrameMeta()
                    meta.ParseFromString(env.payload)
                    n = self._processor.process(meta, env.occurred_at_ms)
                    self.frames_processed += 1
                    self.signals_published += n
                    consumer.commit(msg, asynchronous=False)
                except Exception:  # noqa: BLE001 — one bad frame must not kill the loop
                    log.exception("failed to process frame; committing to skip")
                    consumer.commit(msg, asynchronous=False)
        finally:
            consumer.close()
            log.info("frame consumer stopped")
