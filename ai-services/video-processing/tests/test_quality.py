import numpy as np
import pytest

from app.domain import quality


def test_sharp_image_scores_higher_than_blurred():
    rng = np.random.default_rng(0)
    sharp = (rng.integers(0, 256, size=(64, 64, 3))).astype(np.uint8)  # high-freq noise
    flat = np.full((64, 64, 3), 128, dtype=np.uint8)                   # no detail
    assert quality.sharpness(sharp) > quality.sharpness(flat)
    assert quality.sharpness(flat) == 0.0


def test_illumination_peaks_at_mid_brightness():
    mid = np.full((16, 16, 3), 128, dtype=np.uint8)
    dark = np.full((16, 16, 3), 5, dtype=np.uint8)
    bright = np.full((16, 16, 3), 250, dtype=np.uint8)
    assert quality.illumination(mid) > quality.illumination(dark)
    assert quality.illumination(mid) > quality.illumination(bright)
    assert quality.illumination(mid) == pytest.approx(1.0, abs=0.01)


def test_size_score_saturates_and_clamps():
    assert quality.size_score(0.0, 100.0) == 0.0
    assert quality.size_score(5.0, 100.0) == 0.5   # 5% of frame -> 0.5
    assert quality.size_score(50.0, 100.0) == 1.0  # >=10% -> clamped to 1.0


def test_composite_is_in_unit_range():
    crop = np.full((32, 32, 3), 128, dtype=np.uint8)
    q = quality.composite_quality(crop, face_area=200.0, frame_area=1000.0)
    assert 0.0 <= q <= 1.0
