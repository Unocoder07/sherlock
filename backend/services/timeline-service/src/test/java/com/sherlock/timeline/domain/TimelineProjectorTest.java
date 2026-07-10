package com.sherlock.timeline.domain;

import com.sherlock.timeline.domain.TimelineProjector.Kind;
import com.sherlock.timeline.domain.TimelineProjector.Projected;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineProjectorTest {

    private static final String M = "m1";
    private static final String P = "p1";

    private final TimelineProjector projector = new TimelineProjector(0.15);

    @Test
    void firstSightWithoutTransitionProducesNoEntry() {
        assertThat(projector.project(M, P, "OBSERVING", "OBSERVING", 0.1)).isEmpty();
    }

    @Test
    void stateChangeProducesTransitionEntry() {
        projector.project(M, P, "OBSERVING", "OBSERVING", 0.1);

        Optional<Projected> out = projector.project(M, P, "UNCERTAIN", "OBSERVING", 0.4);

        assertThat(out).hasValueSatisfying(p -> {
            assertThat(p.kind()).isEqualTo(Kind.STATE_TRANSITION);
            assertThat(p.fromState()).isEqualTo("OBSERVING");
            assertThat(p.toState()).isEqualTo("UNCERTAIN");
        });
    }

    @Test
    void scoreMoveBeyondThresholdWithoutStateChangeIsInflection() {
        projector.project(M, P, "IDENTIFIED", "UNCERTAIN", 0.60);   // transition, baseline=0.60

        Optional<Projected> out = projector.project(M, P, "IDENTIFIED", "IDENTIFIED", 0.80);

        assertThat(out).hasValueSatisfying(p -> assertThat(p.kind()).isEqualTo(Kind.SCORE_INFLECTION));
    }

    @Test
    void smallScoreDriftBelowThresholdIsIgnored() {
        projector.project(M, P, "IDENTIFIED", "UNCERTAIN", 0.60);   // transition, baseline=0.60

        assertThat(projector.project(M, P, "IDENTIFIED", "IDENTIFIED", 0.66)).isEmpty();
    }

    @Test
    void transitionRebaselinesInflectionDetector() {
        projector.project(M, P, "IDENTIFIED", "UNCERTAIN", 0.90);   // baseline reset to 0.90

        // Drop of only 0.10 from the new baseline — below threshold, no inflection.
        assertThat(projector.project(M, P, "IDENTIFIED", "IDENTIFIED", 0.80)).isEmpty();
    }

    @Test
    void participantsTrackedIndependently() {
        projector.project(M, P, "IDENTIFIED", "UNCERTAIN", 0.60);   // p1 baseline 0.60
        projector.project(M, "p2", "OBSERVING", "OBSERVING", 0.10); // p2 first sight

        // p2's own +0.30 move earns an inflection, unaffected by p1's baseline.
        assertThat(projector.project(M, "p2", "OBSERVING", "OBSERVING", 0.40))
                .hasValueSatisfying(p -> assertThat(p.kind()).isEqualTo(Kind.SCORE_INFLECTION));
    }
}
