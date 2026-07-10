"""Frame quality scoring (0..1) — pure numpy, no cv2 so it stays unit-testable.

Composite of three cheap proxies over the face crop:
  * sharpness   — variance of the discrete Laplacian (low => blurry)
  * illumination— mean brightness, penalised at the dark/blown-out extremes
  * size        — face area vs frame area (tiny faces are low quality)
"""
from __future__ import annotations

import numpy as np


def _to_gray(crop: np.ndarray) -> np.ndarray:
    if crop.ndim == 3:
        # BGR/RGB -> luminance (order-agnostic, coefficients are close enough).
        return crop[..., :3].mean(axis=2)
    return crop.astype(np.float64)


def sharpness(crop: np.ndarray) -> float:
    """Normalized Laplacian variance in ~0..1 (saturates for very sharp crops)."""
    g = _to_gray(crop).astype(np.float64)
    if g.size < 9:
        return 0.0
    # 4-neighbour discrete Laplacian via array shifts.
    lap = (
        -4.0 * g[1:-1, 1:-1]
        + g[:-2, 1:-1] + g[2:, 1:-1] + g[1:-1, :-2] + g[1:-1, 2:]
    )
    var = float(lap.var())
    # ~200 var is already crisp; map with a soft saturation.
    return float(min(1.0, var / 200.0))


def illumination(crop: np.ndarray) -> float:
    """1.0 near mid-brightness, decaying toward pure black/white."""
    g = _to_gray(crop)
    mean = float(g.mean()) / 255.0
    # Triangular around 0.5: 1.0 at 0.5, 0.0 at 0 or 1.
    return float(max(0.0, 1.0 - abs(mean - 0.5) / 0.5))


def size_score(face_area: float, frame_area: float) -> float:
    if frame_area <= 0:
        return 0.0
    ratio = face_area / frame_area
    # A face covering >= 10% of the frame is "full quality" on this axis.
    return float(min(1.0, ratio / 0.10))


def composite_quality(face_crop: np.ndarray, face_area: float, frame_area: float) -> float:
    """Weighted blend of the three proxies, clamped to 0..1."""
    s = sharpness(face_crop)
    ill = illumination(face_crop)
    sz = size_score(face_area, frame_area)
    score = 0.5 * s + 0.3 * ill + 0.2 * sz
    return float(max(0.0, min(1.0, score)))
