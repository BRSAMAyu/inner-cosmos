package com.innercosmos.service;

import com.innercosmos.ai.claim.ClaimCandidate;
import com.innercosmos.entity.DialogMessage;
import java.util.List;

/**
 * Campaign B — automatic user-model claim extraction. Derives typed claim CANDIDATES (never
 * authoritative facts) from conversation evidence. A real Provider may do the extraction when
 * configured; a deterministic engine is the fallback, and every candidate is sanitized to the
 * precision-first invariants (known type, provenance that references real messages) regardless of
 * source.
 */
public interface ClaimExtractionService {

    /**
     * @param userId   the owner; used for provider routing/AB assignment only.
     * @param messages conversation messages in chronological order (mixed speakers allowed).
     * @return sanitized candidates, or empty when the user said nothing claim-worthy.
     */
    List<ClaimCandidate> extract(Long userId, List<DialogMessage> messages);
}
