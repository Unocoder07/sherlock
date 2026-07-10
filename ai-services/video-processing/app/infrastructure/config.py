"""Pydantic settings — all runtime config comes from the environment.

Defaults are the in-container values (kafka:9092, http://minio:9000). A host run
(e.g. an IDE debug session) overrides via env: KAFKA_BOOTSTRAP_SERVERS=localhost:29092,
MINIO_ENDPOINT=http://localhost:9000.
"""
from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", case_sensitive=False)

    server_port: int = 8098

    # Kafka
    kafka_bootstrap_servers: str = "kafka:9092"
    consumer_group: str = "video-processing"

    # MinIO / object store
    minio_endpoint: str = "http://minio:9000"
    minio_access_key: str = "sherlock"
    minio_secret_key: str = "sherlock123"
    bucket_media: str = "sherlock-media"
    bucket_embeddings: str = "sherlock-embeddings"

    # Face detection / processing knobs
    det_size: int = 640            # InsightFace detector input square
    det_threshold: float = 0.5     # min detection score to count a face
    lip_window: int = 8            # rolling frames for the lip-activity smoother
    lip_gain: float = 12.0         # maps lower-face motion → 0..1 lip-activity

    @property
    def minio_secure(self) -> bool:
        return self.minio_endpoint.lower().startswith("https://")

    @property
    def minio_host(self) -> str:
        """`host:port` form the minio client expects (scheme stripped)."""
        ep = self.minio_endpoint
        for scheme in ("https://", "http://"):
            if ep.lower().startswith(scheme):
                return ep[len(scheme):]
        return ep


settings = Settings()
