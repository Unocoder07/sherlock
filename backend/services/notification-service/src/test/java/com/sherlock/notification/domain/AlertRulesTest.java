package com.sherlock.notification.domain;

import com.sherlock.notification.domain.AlertRules.Alert;
import com.sherlock.notification.domain.AlertRules.Severity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRulesTest {

    private static final String M = "m1";
    private static final String P = "p1";

    private final AlertRules rules = new AlertRules(30_000);

    @Test
    void proxyTransitionRaisesCritical() {
        Optional<Alert> a = rules.evaluate(M, P, "PROXY_SUSPECTED", "IDENTIFIED", 1_000);

        assertThat(a).hasValueSatisfying(alert -> {
            assertThat(alert.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(alert.rule()).isEqualTo("PROXY_SUSPECTED");
            assertThat(alert.title()).isEqualTo("Possible proxy detected");
        });
    }

    @Test
    void candidateSwitchedRaisesCritical() {
        assertThat(rules.evaluate(M, P, "CANDIDATE_SWITCHED", "IDENTIFIED", 1_000))
                .hasValueSatisfying(a -> assertThat(a.severity()).isEqualTo(Severity.CRITICAL));
    }

    @Test
    void identifiedRaisesInfoAndSignalLostRaisesWarning() {
        assertThat(rules.evaluate(M, P, "IDENTIFIED", "UNCERTAIN", 1_000))
                .hasValueSatisfying(a -> assertThat(a.severity()).isEqualTo(Severity.INFO));
        assertThat(rules.evaluate(M, P, "SIGNAL_LOST", "IDENTIFIED", 2_000))
                .hasValueSatisfying(a -> assertThat(a.severity()).isEqualTo(Severity.WARNING));
    }

    @Test
    void noAlertWhenStateUnchanged() {
        assertThat(rules.evaluate(M, P, "IDENTIFIED", "IDENTIFIED", 1_000)).isEmpty();
    }

    @Test
    void uninterestingStateRaisesNothing() {
        assertThat(rules.evaluate(M, P, "UNCERTAIN", "OBSERVING", 1_000)).isEmpty();
    }

    @Test
    void repeatWithinCooldownIsThrottled() {
        rules.evaluate(M, P, "PROXY_SUSPECTED", "IDENTIFIED", 1_000);

        // Same rule re-entered 5s later (< 30s cooldown): suppressed.
        assertThat(rules.evaluate(M, P, "PROXY_SUSPECTED", "IDENTIFIED", 6_000)).isEmpty();
    }

    @Test
    void repeatAfterCooldownFiresAgain() {
        rules.evaluate(M, P, "PROXY_SUSPECTED", "IDENTIFIED", 1_000);

        assertThat(rules.evaluate(M, P, "PROXY_SUSPECTED", "IDENTIFIED", 40_000)).isPresent();
    }

    @Test
    void differentParticipantsNotThrottledTogether() {
        rules.evaluate(M, P, "PROXY_SUSPECTED", "IDENTIFIED", 1_000);

        assertThat(rules.evaluate(M, "p2", "PROXY_SUSPECTED", "IDENTIFIED", 2_000)).isPresent();
    }
}
