package com.sherlock.confidence.domain;

/**
 * Pure scoring math for the belief model (doc 05 §3–§4). No state, no framework —
 * every function is a deterministic function of its inputs so scoring is
 * replay-safe and trivially unit-testable.
 */
public final class Scoring {

    private Scoring() {
    }

    /**
     * Exponential time decay {@code exp(-Δt / τ)} (doc 05 §3). Old evidence fades
     * rather than persisting forever — this is what makes the engine robust to
     * camera-off / audio-drop: absence lowers certainty without flipping the verdict.
     *
     * @param dtMillis        elapsed time since the observation (now − t_e)
     * @param halfLifeSeconds the decay time constant τ for this evidence type
     * @return decay factor in (0,1]; 1.0 when fresh, →0 as it ages
     */
    public static double decay(long dtMillis, double halfLifeSeconds) {
        if (dtMillis <= 0) {
            return 1.0;
        }
        double dtSeconds = dtMillis / 1000.0;
        return Math.exp(-dtSeconds / halfLifeSeconds);
    }

    /**
     * A single evidence record's signed contribution to the log-odds accumulator
     * (doc 05 §3): {@code polarity · weight · reliability · magnitude · decay}.
     */
    public static double contribution(int polarity,
                                      double weight,
                                      double reliability,
                                      double magnitude,
                                      double decay) {
        return polarity * weight * reliability * magnitude * decay;
    }

    /** Logistic squashing to (0,1). */
    public static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
