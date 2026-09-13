package com.innercosmos.service.continuity;

import java.util.List;

/**
 * CP-18 cross-session real continuity: the opening context for a new conversation is built
 * from what genuinely happened before — the prior session's durable summary and the user's
 * recent memories — with explicit provenance labels. A first-time user gets an honest
 * "this is our first conversation" with ZERO fabricated memory references; the service
 * never invents continuity (J02/deactivation semantics).
 *
 * <p>CP-18 closing-checklist §2-13 adds the owner's VISIBILITY withdrawal switch: the user
 * may stop the opening card from being shown. That switch is a display choice only —
 * {@link #openingContext(Long)} keeps reporting the honest facts (recording never stops and
 * never fabricates "nothing happened"); the SUPPLY endpoint layers the withdrawn marker on
 * top of it when the switch is off.
 */
public interface SessionContinuityService {

    /**
     * Honest opening context. {@code openingVisible} is true unless the owner withdrew
     * opening visibility; when false the payload is the withdrawn marker — no prior
     * material is supplied, and {@code hasPrior=false} means "nothing supplied here",
     * never the false claim that no prior conversation exists.
     */
    record OpeningContext(
            boolean hasPrior,
            Long priorSessionId,
            String priorActiveAt,
            List<CarryNote> carryForward,
            String openingLine,
            boolean openingVisible) {

        /** Legacy 5-arg shape (pre-§2-13 call sites): no withdrawal on record, visible. */
        public OpeningContext(boolean hasPrior, Long priorSessionId, String priorActiveAt,
                              List<CarryNote> carryForward, String openingLine) {
            this(hasPrior, priorSessionId, priorActiveAt, carryForward, openingLine, true);
        }

        /** CP-18 §2-13: the explicit withdrawn marker — honest about being a choice. */
        public static OpeningContext withdrawn() {
            return new OpeningContext(false, null, null, List.of(),
                    "开屏连续性已按你的选择关闭。", false);
        }
    }

    record CarryNote(String kind, String text, String provenance) {
    }

    /** CP-18 §2-13: the owner's visibility switch state (default open when no row exists). */
    record VisibilityState(boolean openingVisible) {
    }

    /** Build the honest opening context for the user's NEXT conversation. */
    OpeningContext openingContext(Long userId);

    /** CP-18 §2-13: the current visibility switch state (default open). */
    VisibilityState visibility(Long userId);

    /** CP-18 §2-13: set the visibility switch (owner-scoped by the caller's user id). */
    VisibilityState setOpeningVisible(Long userId, boolean openingVisible);
}
