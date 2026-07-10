package com.sherlock.explanation.domain;

import java.util.List;
import java.util.Map;

/**
 * Pure presentation logic (doc 02 §7): turns the Confidence Engine's numeric
 * per-evidence-type contributions into ranked English reasons, and derives a
 * one-line headline from the participant's state. No framework, no proto — so
 * wording/i18n can evolve and be unit-tested in isolation.
 *
 * <p>Templates are keyed on the {@code EVIDENCE_TYPE_*} names the engine emits
 * ({@code EvidenceType.name()}); anything unknown falls back to a polarity-framed
 * generic sentence so a new evidence type never renders blank.
 */
public class ReasonRenderer {

    /** How many reasons to surface (ranked by magnitude). */
    private static final int MAX_REASONS = 5;

    private static final String STATE_PREFIX = "PARTICIPANT_STATE_";

    /** evidence type -> fixed English sentence (polarity is baked into the glyph). */
    private static final Map<String, String> TEMPLATES = Map.ofEntries(
            Map.entry("EVIDENCE_TYPE_AV_BINDING", "✓ Face and voice belong to the same person"),
            Map.entry("EVIDENCE_TYPE_FACE_MATCH", "✓ Face matches the established identity"),
            Map.entry("EVIDENCE_TYPE_VOICE_MATCH", "✓ Voice matches the established identity"),
            Map.entry("EVIDENCE_TYPE_DOMINANCE", "✓ Voice stayed dominant in the conversation"),
            Map.entry("EVIDENCE_TYPE_FACE_PRESENT", "✓ Candidate's face is present on camera"),
            Map.entry("EVIDENCE_TYPE_SCREEN_SHARE", "• Screen sharing is active"),
            Map.entry("EVIDENCE_TYPE_MEETING_EVENT", "• Meeting activity recorded"),
            Map.entry("EVIDENCE_TYPE_METADATA_NAME", "• Display name matches (weak signal)"),
            Map.entry("EVIDENCE_TYPE_ANCHOR_MISMATCH", "⚠ Present person doesn't match the established identity"),
            Map.entry("EVIDENCE_TYPE_AV_BINDING_BROKEN", "⚠ The active speaker appears to be off-screen"),
            Map.entry("EVIDENCE_TYPE_FACE_CHANGED", "⚠ Face changed mid-interview"),
            Map.entry("EVIDENCE_TYPE_VOICE_CHANGED", "⚠ Voice changed mid-interview"),
            Map.entry("EVIDENCE_TYPE_MULTIPLE_PRESENCE", "⚠ More than one person detected"));

    /** state (clean or prefixed) -> one-line headline. */
    private static final Map<String, String> HEADLINES = Map.ofEntries(
            Map.entry("OBSERVING", "Observing — no identity formed yet"),
            Map.entry("ANCHORING", "Establishing the candidate's identity…"),
            Map.entry("UNCERTAIN", "Identity uncertain"),
            Map.entry("IDENTIFIED", "Candidate identified"),
            Map.entry("PROXY_SUSPECTED", "Possible proxy detected"),
            Map.entry("CANDIDATE_SWITCHED", "Candidate may have been switched"),
            Map.entry("SIGNAL_LOST", "Signal lost"),
            Map.entry("LEFT", "Participant left"));

    /** A numeric contribution as it arrives from the verdict (proto-free view). */
    public record Contribution(String evidenceType, double value, double weight, int polarity) {
    }

    /** A rendered reason ready for the wire/UI. */
    public record Reason(String text, String evidenceType, int polarity, double magnitude) {
    }

    /** The full rendering: a headline plus ranked reasons. */
    public record Rendered(String headline, List<Reason> reasons) {
    }

    /**
     * Render a verdict's state + contributions into a headline and ranked reasons.
     * Reasons are ordered by magnitude (|value| · weight) descending and capped.
     */
    public Rendered render(String state, List<Contribution> contributions) {
        List<Reason> reasons = contributions.stream()
                .map(this::toReason)
                .sorted((a, b) -> Double.compare(b.magnitude(), a.magnitude()))
                .limit(MAX_REASONS)
                .toList();
        return new Rendered(headline(state), reasons);
    }

    /** "PARTICIPANT_STATE_IDENTIFIED" or "IDENTIFIED" -> a human headline. */
    public String headline(String state) {
        String clean = cleanState(state);
        return HEADLINES.getOrDefault(clean, clean);
    }

    private Reason toReason(Contribution c) {
        double magnitude = Math.abs(c.value()) * c.weight();
        return new Reason(template(c), c.evidenceType(), c.polarity(), magnitude);
    }

    private String template(Contribution c) {
        String t = TEMPLATES.get(c.evidenceType());
        if (t != null) {
            return t;
        }
        // Unknown evidence type: frame generically by polarity so it's never blank.
        String label = humanize(c.evidenceType());
        return c.polarity() < 0 ? "⚠ " + label : "✓ " + label;
    }

    /** "EVIDENCE_TYPE_FACE_MATCH" -> "Face match". */
    static String humanize(String evidenceType) {
        String s = evidenceType.startsWith("EVIDENCE_TYPE_")
                ? evidenceType.substring("EVIDENCE_TYPE_".length())
                : evidenceType;
        s = s.replace('_', ' ').toLowerCase();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "PARTICIPANT_STATE_IDENTIFIED" -> "IDENTIFIED". */
    public static String cleanState(String enumName) {
        return enumName.startsWith(STATE_PREFIX) ? enumName.substring(STATE_PREFIX.length()) : enumName;
    }
}
