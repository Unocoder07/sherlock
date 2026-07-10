package com.sherlock.notification.domain;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Pure alert policy (doc 02 §8): maps a state transition to an alert and throttles
 * repeats. No proto, no persistence — decisions only, so the rules and throttle are
 * unit-testable. An alert fires only on the transition <em>into</em> a state of
 * interest (not on every re-emission of the same state), and a repeat of the same
 * {@code (meeting, participant, rule)} within the cooldown (event-time) is suppressed.
 */
public class AlertRules {

    /** Alert severity (mirrors the proto {@code Severity}). */
    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    /** A decided alert, sans identity/persistence concerns. */
    public record Alert(String rule, Severity severity, String title, String message, String state) {
    }

    /** Static rule table: which target state raises what alert. */
    private record Rule(Severity severity, String title, String message) {
    }

    private static final Map<String, Rule> RULES = Map.of(
            "PROXY_SUSPECTED", new Rule(Severity.CRITICAL,
                    "Possible proxy detected",
                    "The present person no longer matches the established identity."),
            "CANDIDATE_SWITCHED", new Rule(Severity.CRITICAL,
                    "Candidate switched",
                    "The candidate on the call appears to have changed mid-interview."),
            "SIGNAL_LOST", new Rule(Severity.WARNING,
                    "Signal lost",
                    "Face and voice signals dropped — the candidate may be off-camera or muted."),
            "IDENTIFIED", new Rule(Severity.INFO,
                    "Candidate identified",
                    "The system has confidently identified the candidate."));

    private final long cooldownMs;

    /** meetingId:participantId:rule -> event-time of the last fire. */
    private final ConcurrentMap<String, Long> lastFired = new ConcurrentHashMap<>();

    public AlertRules(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    /**
     * Evaluate a verdict transition. Returns an alert if the transition entered a
     * state of interest and the throttle window has elapsed.
     *
     * @param state        current clean state label
     * @param prevState    previous clean state label
     * @param occurredAtMs event time of the verdict (drives throttling)
     */
    public Optional<Alert> evaluate(String meetingId, String participantId,
                                    String state, String prevState, long occurredAtMs) {
        if (state.equals(prevState)) {
            return Optional.empty();   // no transition — not entering the state now
        }
        Rule rule = RULES.get(state);
        if (rule == null) {
            return Optional.empty();
        }
        String key = meetingId + ":" + participantId + ":" + state;
        Long last = lastFired.get(key);
        if (last != null && occurredAtMs - last < cooldownMs) {
            return Optional.empty();   // throttled
        }
        lastFired.put(key, occurredAtMs);
        return Optional.of(new Alert(state, rule.severity(), rule.title(), rule.message(), state));
    }
}
