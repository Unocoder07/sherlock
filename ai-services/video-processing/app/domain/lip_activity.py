"""Lip-activity heuristic (the video half of audio-visual binding).

Landmark-free by design: instead of depending on a face-mesh model, we measure
frame-to-frame *motion in the lower-central face region* (where the mouth is). A
talking mouth changes those pixels between frames; a still/closed mouth does not.
Per participant we keep the previous mouth ROI plus a short rolling buffer so the
emitted score (0..1) is smoothed over a window rather than a single noisy frame.

Pure numpy — unit-testable with synthetic ROIs; the caller supplies an already
cropped, grayscale lower-face patch.
"""
from __future__ import annotations

from collections import deque

import numpy as np

_GRID = 16  # ROIs are mean-pooled to GRID x GRID before comparison (scale-invariant).


def pool_roi(gray_roi: np.ndarray, grid: int = _GRID) -> np.ndarray:
    """Mean-pool an arbitrary-sized grayscale ROI down to a fixed grid (float 0..255)."""
    g = gray_roi.astype(np.float64)
    if g.ndim != 2 or g.size == 0:
        return np.zeros((grid, grid), dtype=np.float64)
    h, w = g.shape
    rows = np.array_split(g, min(grid, h), axis=0)
    pooled_rows = [np.array_split(r.mean(axis=0, keepdims=True), min(grid, w), axis=1) for r in rows]
    out = np.array([[blk.mean() for blk in cols] for cols in pooled_rows])
    # Pad to grid x grid if the ROI was smaller than the grid.
    if out.shape != (grid, grid):
        padded = np.zeros((grid, grid), dtype=np.float64)
        padded[: out.shape[0], : out.shape[1]] = out
        out = padded
    return out


class LipActivityTracker:
    """Per-participant motion smoother. Not thread-safe; use one per consumer thread."""

    def __init__(self, window: int = 8, gain: float = 12.0) -> None:
        self._window = window
        self._gain = gain
        self._prev: dict[str, np.ndarray] = {}
        self._hist: dict[str, deque[float]] = {}

    def update(self, participant_id: str, gray_roi: np.ndarray) -> float:
        """Feed the current mouth ROI, return the smoothed lip-activity in 0..1."""
        cur = pool_roi(gray_roi)
        prev = self._prev.get(participant_id)
        self._prev[participant_id] = cur

        if prev is None:
            motion = 0.0
        else:
            motion = float(np.abs(cur - prev).mean()) / 255.0

        hist = self._hist.setdefault(participant_id, deque(maxlen=self._window))
        hist.append(motion)
        avg_motion = sum(hist) / len(hist)
        return float(max(0.0, min(1.0, avg_motion * self._gain)))

    def reset(self, participant_id: str) -> None:
        """Drop state when the face goes absent so motion isn't measured across a gap."""
        self._prev.pop(participant_id, None)
        self._hist.pop(participant_id, None)
