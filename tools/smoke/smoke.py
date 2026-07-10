"""
Sherlock M0 pipeline smoke test.

Proves the foundation works end-to-end:
  produce  -> Kafka (smoke.test) -> consume  and verify the round-trip.

Messages use an EventEnvelope-shaped payload (JSON form of contracts/common/v1),
keyed by meeting_id — exercising the same envelope + partition-key convention the
real services use. (Production wire format is Protobuf; JSON keeps M0 dependency-free.)

Exit code 0 = PASS, 1 = FAIL. Designed to run as a one-shot container.
"""
import json
import os
import sys
import time
import uuid

from confluent_kafka import Consumer, Producer, KafkaException
from confluent_kafka.admin import AdminClient

BOOTSTRAP = os.environ.get("KAFKA_BOOTSTRAP", "kafka:9092")
TOPIC = os.environ.get("SMOKE_TOPIC", "smoke.test")
N_MESSAGES = int(os.environ.get("SMOKE_MESSAGES", "3"))
TIMEOUT_S = int(os.environ.get("SMOKE_TIMEOUT_S", "30"))

PRODUCER_NAME = "smoke-test@0.1.0"


def log(msg: str) -> None:
    print(f"[smoke] {msg}", flush=True)


def wait_for_broker(deadline: float) -> None:
    """Block until the broker answers metadata, or raise on timeout."""
    admin = AdminClient({"bootstrap.servers": BOOTSTRAP})
    while time.time() < deadline:
        try:
            md = admin.list_topics(timeout=5)
            log(f"broker reachable: {len(md.topics)} topics visible")
            return
        except KafkaException as e:
            log(f"waiting for broker... ({e})")
            time.sleep(2)
    raise TimeoutError(f"broker {BOOTSTRAP} not reachable before timeout")


def envelope(meeting_id: str, seq: int) -> dict:
    now_ms = int(time.time() * 1000)
    return {
        "event_id": str(uuid.uuid4()),
        "event_type": "sherlock.smoke.v1.Ping",
        "schema_version": 1,
        "meeting_id": meeting_id,
        "participant_id": f"participant-{seq}",
        "occurred_at_ms": now_ms,
        "emitted_at_ms": now_ms,
        "producer": PRODUCER_NAME,
        "trace_id": str(uuid.uuid4()),
        "payload": {"seq": seq, "note": "hello from sherlock M0"},
    }


def produce(messages: list[dict]) -> None:
    p = Producer({"bootstrap.servers": BOOTSTRAP, "acks": "all", "enable.idempotence": True})
    delivered = 0

    def on_delivery(err, msg):
        nonlocal delivered
        if err is not None:
            raise KafkaException(err)
        delivered += 1

    for m in messages:
        p.produce(
            TOPIC,
            key=m["meeting_id"].encode(),          # partition key = meeting_id
            value=json.dumps(m).encode(),
            on_delivery=on_delivery,
        )
    p.flush(TIMEOUT_S)
    log(f"produced {delivered}/{len(messages)} messages to '{TOPIC}'")
    if delivered != len(messages):
        raise RuntimeError("not all messages were acknowledged by the broker")


def consume(expected_ids: set[str], deadline: float) -> set[str]:
    c = Consumer(
        {
            "bootstrap.servers": BOOTSTRAP,
            "group.id": f"smoke-{uuid.uuid4()}",
            "auto.offset.reset": "earliest",
            "enable.auto.commit": False,
        }
    )
    c.subscribe([TOPIC])
    seen: set[str] = set()
    try:
        while time.time() < deadline and seen != expected_ids:
            msg = c.poll(1.0)
            if msg is None:
                continue
            if msg.error():
                log(f"consumer error: {msg.error()}")
                continue
            env = json.loads(msg.value())
            if env["event_id"] in expected_ids:
                seen.add(env["event_id"])
                log(
                    f"consumed seq={env['payload']['seq']} "
                    f"key={msg.key().decode()} partition={msg.partition()} "
                    f"({len(seen)}/{len(expected_ids)})"
                )
    finally:
        c.close()
    return seen


def main() -> int:
    log(f"bootstrap={BOOTSTRAP} topic={TOPIC} messages={N_MESSAGES}")
    deadline = time.time() + TIMEOUT_S
    try:
        wait_for_broker(deadline)
        meeting_id = f"meeting-{uuid.uuid4()}"
        messages = [envelope(meeting_id, i) for i in range(N_MESSAGES)]
        expected = {m["event_id"] for m in messages}

        produce(messages)
        seen = consume(expected, time.time() + TIMEOUT_S)

        if seen == expected:
            log(f"round-trip verified: {len(seen)}/{len(expected)} envelopes matched")
            print("\n============================")
            print("   SMOKE TEST PASSED ✅")
            print("============================\n", flush=True)
            return 0

        missing = expected - seen
        log(f"FAILED: missing {len(missing)} of {len(expected)} envelopes")
        print("\n============================")
        print("   SMOKE TEST FAILED ❌")
        print("============================\n", flush=True)
        return 1
    except Exception as e:  # noqa: BLE001 — smoke test: surface any failure clearly
        log(f"ERROR: {e}")
        print("\n   SMOKE TEST FAILED ❌ (exception)\n", flush=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
