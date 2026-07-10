package com.sherlock.explanation.domain;

import com.sherlock.explanation.domain.ReasonRenderer.Contribution;
import com.sherlock.explanation.domain.ReasonRenderer.Rendered;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReasonRendererTest {

    private final ReasonRenderer renderer = new ReasonRenderer();

    @Test
    void mapsKnownEvidenceTypesToEnglishTemplates() {
        Rendered r = renderer.render("PARTICIPANT_STATE_IDENTIFIED", List.of(
                new Contribution("EVIDENCE_TYPE_AV_BINDING", 0.9, 1.0, 1)));

        assertThat(r.reasons()).singleElement()
                .satisfies(reason -> assertThat(reason.text())
                        .isEqualTo("✓ Face and voice belong to the same person"));
    }

    @Test
    void derivesHeadlineFromStateStrippingPrefix() {
        assertThat(renderer.headline("PARTICIPANT_STATE_PROXY_SUSPECTED"))
                .isEqualTo("Possible proxy detected");
        assertThat(renderer.headline("IDENTIFIED")).isEqualTo("Candidate identified");
    }

    @Test
    void ranksReasonsByMagnitudeDescending() {
        Rendered r = renderer.render("IDENTIFIED", List.of(
                new Contribution("EVIDENCE_TYPE_METADATA_NAME", 0.2, 0.1, 1),   // magnitude 0.02
                new Contribution("EVIDENCE_TYPE_DOMINANCE", 0.8, 1.0, 1),       // magnitude 0.80
                new Contribution("EVIDENCE_TYPE_FACE_PRESENT", 0.5, 0.6, 1)));  // magnitude 0.30

        assertThat(r.reasons()).extracting(ReasonRenderer.Reason::evidenceType)
                .containsExactly("EVIDENCE_TYPE_DOMINANCE",
                        "EVIDENCE_TYPE_FACE_PRESENT",
                        "EVIDENCE_TYPE_METADATA_NAME");
    }

    @Test
    void negativeContributionRendersAsWarning() {
        Rendered r = renderer.render("PARTICIPANT_STATE_PROXY_SUSPECTED", List.of(
                new Contribution("EVIDENCE_TYPE_ANCHOR_MISMATCH", 0.7, 1.0, -1)));

        assertThat(r.reasons()).singleElement()
                .satisfies(reason -> {
                    assertThat(reason.polarity()).isEqualTo(-1);
                    assertThat(reason.text()).startsWith("⚠");
                });
    }

    @Test
    void unknownEvidenceTypeFallsBackToPolarityFramedGeneric() {
        Rendered r = renderer.render("UNCERTAIN", List.of(
                new Contribution("EVIDENCE_TYPE_FUTURE_SIGNAL", 0.5, 1.0, -1)));

        assertThat(r.reasons()).singleElement()
                .satisfies(reason -> assertThat(reason.text()).isEqualTo("⚠ Future signal"));
    }

    @Test
    void capsReasonsAtFive() {
        List<Contribution> many = List.of(
                new Contribution("EVIDENCE_TYPE_AV_BINDING", 0.9, 1.0, 1),
                new Contribution("EVIDENCE_TYPE_FACE_MATCH", 0.8, 1.0, 1),
                new Contribution("EVIDENCE_TYPE_VOICE_MATCH", 0.7, 1.0, 1),
                new Contribution("EVIDENCE_TYPE_DOMINANCE", 0.6, 1.0, 1),
                new Contribution("EVIDENCE_TYPE_FACE_PRESENT", 0.5, 1.0, 1),
                new Contribution("EVIDENCE_TYPE_METADATA_NAME", 0.4, 1.0, 1));

        assertThat(renderer.render("IDENTIFIED", many).reasons()).hasSize(5);
    }
}
