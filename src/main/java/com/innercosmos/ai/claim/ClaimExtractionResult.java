package com.innercosmos.ai.claim;

import java.util.List;

/** Container for the (real-provider or deterministic) claim extraction output. */
public record ClaimExtractionResult(List<ClaimCandidate> candidates) {
    public ClaimExtractionResult {
        candidates = candidates == null ? List.of() : candidates;
    }
}
