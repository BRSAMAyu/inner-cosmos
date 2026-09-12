package com.innercosmos.service.continuity;

import java.util.List;

/**
 * CP-18 cross-session real continuity: the opening context for a new conversation is built
 * from what genuinely happened before — the prior session's durable summary and the user's
 * recent memories — with explicit provenance labels. A first-time user gets an honest
 * "this is our first conversation" with ZERO fabricated memory references; the service
 * never invents continuity (J02/deactivation semantics).
 */
public interface SessionContinuityService {

    record OpeningContext(
            boolean hasPrior,
            Long priorSessionId,
            String priorActiveAt,
            List<CarryNote> carryForward,
            String openingLine) {
    }

    record CarryNote(String kind, String text, String provenance) {
    }

    /** Build the honest opening context for the user's NEXT conversation. */
    OpeningContext openingContext(Long userId);
}
