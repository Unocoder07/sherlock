"""Stable identifiers shared across the service."""

PRODUCER = "video-processing@0.1.0"

# Kafka topics (see infra/local/kafka/create-topics.sh).
TOPIC_MEDIA_FRAMES = "media.frames.meta"
TOPIC_VIDEO_SIGNALS = "video.signals"

# Fully-qualified proto type names (EventEnvelope.event_type discriminator).
TYPE_MEDIA_FRAME_META = "sherlock.media.v1.MediaFrameMeta"
TYPE_VIDEO_SIGNAL = "sherlock.signals.v1.VideoSignal"
