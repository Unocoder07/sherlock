"""EventEnvelope wrap/parse — the Python mirror of the Java `EnvelopeCodec`.

Every message on the Kafka bus is an `EventEnvelope` (contracts/proto/.../envelope.proto):
the typed payload is carried as serialized bytes in `payload`, discriminated by
`event_type` (the payload's fully-qualified proto name). Partition key = meeting_id.
"""
from __future__ import annotations

import time
import uuid

from google.protobuf.message import Message
from sherlock.common.v1 import envelope_pb2


def wrap(
    payload: Message,
    *,
    producer: str,
    meeting_id: str,
    participant_id: str,
    occurred_at_ms: int,
    schema_version: int = 1,
    trace_id: str = "",
) -> bytes:
    """Serialize a typed payload inside an EventEnvelope and return the wire bytes."""
    env = envelope_pb2.EventEnvelope(
        event_id=str(uuid.uuid4()),
        event_type=payload.DESCRIPTOR.full_name,
        schema_version=schema_version,
        meeting_id=meeting_id,
        participant_id=participant_id,
        occurred_at_ms=occurred_at_ms,
        emitted_at_ms=int(time.time() * 1000),
        producer=producer,
        trace_id=trace_id,
        payload=payload.SerializeToString(),
    )
    return env.SerializeToString()


def parse(value: bytes) -> envelope_pb2.EventEnvelope:
    """Parse raw Kafka bytes back into an EventEnvelope."""
    env = envelope_pb2.EventEnvelope()
    env.ParseFromString(value)
    return env
