import numpy as np

from app.domain.lip_activity import LipActivityTracker, pool_roi


def test_pool_roi_returns_fixed_grid():
    roi = np.random.default_rng(1).integers(0, 256, size=(37, 41)).astype(np.uint8)
    pooled = pool_roi(roi, grid=16)
    assert pooled.shape == (16, 16)


def test_first_frame_has_no_motion():
    t = LipActivityTracker(window=8, gain=12.0)
    roi = np.full((20, 20), 100, dtype=np.uint8)
    assert t.update("p1", roi) == 0.0


def test_still_mouth_stays_low_moving_mouth_rises():
    t = LipActivityTracker(window=8, gain=12.0)
    still = np.full((20, 20), 100, dtype=np.uint8)
    t.update("p1", still)
    low = t.update("p1", still)  # identical frame -> ~0 motion

    rng = np.random.default_rng(2)
    prev = rng.integers(0, 256, size=(20, 20)).astype(np.uint8)
    t.update("p2", prev)
    moving = t.update("p2", 255 - prev)  # large pixel change

    assert low == 0.0
    assert moving > low


def test_reset_clears_participant_state():
    t = LipActivityTracker()
    roi = np.full((20, 20), 50, dtype=np.uint8)
    t.update("p1", roi)
    t.reset("p1")
    # After reset the next frame is treated as a first frame again (no motion).
    assert t.update("p1", roi) == 0.0
