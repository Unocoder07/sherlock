package com.sherlock.confidence.domain;

import com.sherlock.contracts.evidence.v1.EvidenceSource;
import com.sherlock.contracts.evidence.v1.EvidenceType;

/**
 * A single normalized observation the Confidence Engine consumes (from
 * {@code evidence.events}). Weight and polarity are NOT carried here — they are
 * intrinsic to the evidence type and applied from the {@link WeightPolicy}, so
 * tuning stays in one place (doc 05 §9). The observation supplies only what is
 * observation-specific: strength ({@code magnitude}) and confidence
 * ({@code reliability}), plus its event time.
 *
 * @param magnitude normalized strength 0..1 (e.g. calibrated cosine match score)
 * @param reliability observation confidence 0..1 (low SNR / blur → low)
 */
public record Evidence(
        String participantId,
        EvidenceType type,
        EvidenceSource source,
        double magnitude,
        double reliability,
        long occurredAtMs) {
}
