"""Pure helpers over a list of detected faces (framework-agnostic).

A "face" here is any object exposing `bbox` (x1, y1, x2, y2) and `det_score` — which
is exactly what InsightFace's `FaceAnalysis.get()` returns. Keeping these as free
functions (rather than reaching into the model) keeps selection logic unit-testable.
"""
from __future__ import annotations


def bbox_area(bbox) -> float:
    x1, y1, x2, y2 = bbox
    return float(max(0.0, x2 - x1) * max(0.0, y2 - y1))


def largest_face(faces: list):
    """The dominant face = the one with the biggest bounding box (nearest speaker)."""
    if not faces:
        return None
    return max(faces, key=lambda f: bbox_area(f.bbox))


def count_confident(faces: list, threshold: float) -> int:
    """How many faces clear the detection-score threshold."""
    return sum(1 for f in faces if float(getattr(f, "det_score", 0.0)) >= threshold)
