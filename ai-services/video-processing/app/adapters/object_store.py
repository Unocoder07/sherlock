"""MinIO/S3 object-store adapter.

Kafka never carries media bytes — only `s3://bucket/key` refs. This adapter fetches
frame JPEGs by ref and stores embedding vectors, returning the ref that goes onto
`video.signals` for the Identity Anchor Service to fetch later.
"""
from __future__ import annotations

import io

import numpy as np
from minio import Minio

from app.infrastructure.config import settings


def _client() -> Minio:
    return Minio(
        settings.minio_host,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_secure,
    )


def parse_s3_uri(uri: str) -> tuple[str, str]:
    """`s3://bucket/a/b/c.jpg` -> ("bucket", "a/b/c.jpg")."""
    if not uri.startswith("s3://"):
        raise ValueError(f"not an s3 uri: {uri}")
    without = uri[len("s3://"):]
    bucket, _, key = without.partition("/")
    if not bucket or not key:
        raise ValueError(f"malformed s3 uri: {uri}")
    return bucket, key


class ObjectStore:
    def __init__(self) -> None:
        self._c = _client()

    def get_bytes(self, uri: str) -> bytes:
        bucket, key = parse_s3_uri(uri)
        resp = self._c.get_object(bucket, key)
        try:
            return resp.read()
        finally:
            resp.close()
            resp.release_conn()

    def put_embedding(self, meeting_id: str, participant_id: str, sequence: int,
                      embedding: np.ndarray) -> str:
        """Store a .npy embedding and return its s3:// ref."""
        key = f"{meeting_id}/{participant_id}/{sequence}.npy"
        buf = io.BytesIO()
        np.save(buf, embedding.astype(np.float32), allow_pickle=False)
        data = buf.getvalue()
        self._c.put_object(
            settings.bucket_embeddings,
            key,
            io.BytesIO(data),
            length=len(data),
            content_type="application/octet-stream",
        )
        return f"s3://{settings.bucket_embeddings}/{key}"
