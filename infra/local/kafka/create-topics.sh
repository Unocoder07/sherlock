#!/usr/bin/env bash
# Creates the Sherlock Kafka topics (idempotent). Run by the kafka-init container.
# Local single-broker => replication-factor 1. Partition counts mirror the design
# (docs/architecture/03-event-driven-design.md), scaled down where sensible for dev.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"

# topic:partitions:retention_ms   (retention -1 = use broker default)
TOPICS=(
  "meeting.events:6:2592000000"        # 30d
  "media.frames.meta:12:21600000"      # 6h  (high volume, refs only)
  "video.signals:12:604800000"         # 7d
  "audio.signals:12:604800000"         # 7d
  "identity.anchor:6:2592000000"       # 30d (self-built reference lifecycle + consistency)
  "evidence.events:6:2592000000"       # 30d
  "confidence.updates:6:2592000000"    # 30d
  "verdict.enriched:6:2592000000"      # 30d (verdict + English reasons, for WS fan-out)
  "timeline.events:6:2592000000"       # 30d
  "notifications:3:7776000000"         # 90d
  "smoke.test:1:3600000"               # 1h  (M0 pipeline validation)
)

echo "Waiting for Kafka at ${BOOTSTRAP} ..."
until kafka-topics --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; do
  sleep 2
done

for entry in "${TOPICS[@]}"; do
  IFS=":" read -r name parts retention <<< "${entry}"
  echo "Creating topic '${name}' (partitions=${parts}, retention.ms=${retention})"
  kafka-topics --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${name}" \
    --partitions "${parts}" \
    --replication-factor 1 \
    --config "retention.ms=${retention}"
done

echo "───────────────────────────────────────────────"
echo "Topics present:"
kafka-topics --bootstrap-server "${BOOTSTRAP}" --list
echo "kafka-init complete."
