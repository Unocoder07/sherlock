"""FastAPI entrypoint: health/metrics + the media-frame consumer.

The throughput path is a Kafka consumer (not HTTP); FastAPI hosts the ops surface
(`/health`, `/metrics`) and owns lifecycle. Models load eagerly at startup so the
compose healthcheck only passes once the service can actually process a frame.
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, generate_latest

from app.adapters.kafka_consumer import FrameConsumer
from app.adapters.kafka_producer import VideoSignalProducer
from app.adapters.object_store import ObjectStore
from app.application.process_frame import FrameProcessor
from app.infrastructure import model_registry

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("video-processing")

FRAMES = Counter("video_frames_processed_total", "Media frames processed")
SIGNALS = Counter("video_signals_published_total", "Video signals published")

_consumer: FrameConsumer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _consumer
    log.info("loading models…")
    model_registry.load()
    processor = FrameProcessor(ObjectStore(), VideoSignalProducer())
    _consumer = FrameConsumer(processor)
    _consumer.start()
    log.info("video-processing ready")
    yield
    if _consumer:
        _consumer.stop()


app = FastAPI(title="Sherlock Video Processing", version="0.1.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict:
    ready = model_registry.is_ready()
    return {"status": "ok" if ready else "starting", "models_loaded": ready}


@app.get("/metrics")
def metrics() -> Response:
    # Surface live counters from the consumer alongside prometheus_client defaults.
    if _consumer:
        FRAMES._value.set(_consumer.frames_processed)      # type: ignore[attr-defined]
        SIGNALS._value.set(_consumer.signals_published)    # type: ignore[attr-defined]
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
