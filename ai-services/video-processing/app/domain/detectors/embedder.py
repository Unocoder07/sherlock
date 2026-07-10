"""Face-embedding extraction helpers.

InsightFace already produces an L2-normalized 512-d ArcFace embedding on each face
(`normed_embedding`). This module just extracts it defensively and re-normalizes so
downstream cosine similarity (in the real Identity Anchor, M6.5) is well-defined.
"""
from __future__ import annotations

import numpy as np

EMBEDDING_DIM = 512


def extract_embedding(face) -> np.ndarray:
    """Return an L2-normalized float32 embedding for a detected face."""
    emb = getattr(face, "normed_embedding", None)
    if emb is None:
        emb = getattr(face, "embedding", None)
    if emb is None:
        raise ValueError("face has no embedding")
    vec = np.asarray(emb, dtype=np.float32).reshape(-1)
    norm = float(np.linalg.norm(vec))
    if norm > 0:
        vec = vec / norm
    return vec.astype(np.float32)
