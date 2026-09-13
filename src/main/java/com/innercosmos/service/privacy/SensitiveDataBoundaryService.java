package com.innercosmos.service.privacy;

/**
 * CP-14 unified sensitive-data boundary. Every consumer-facing read of a user-owned subject
 * goes through one guard that answers: may THIS requester, for THIS purpose, read THIS
 * subject NOW? Enforced at actual-use time: requester status (frozen / minor-restricted
 * accounts fail closed), ownership under the requested purpose, and the CP-15 tombstone
 * (a backup-resurrected row stays unreadable). Admin surfaces are deliberately separate
 * routes — an admin identity never widens a consumer-purpose read here.
 */
public interface SensitiveDataBoundaryService {

    /**
     * P1 subject: 待办 (todo item). Todos are user-owned P1 data but not a CP-15 retraction
     * subject (owner deletion is the only lifecycle), so the guard here enforces ownership and
     * requester state — there is no tombstone dimension for this subject type.
     */
    String SUBJECT_TODO = "TODO";

    /** Consumer read purposes. */
    enum Purpose {
        /** The owner reading/operating their own P1/P2 asset by id. */
        OWNER_READ,
        /**
         * CP-14: a visitor's runtime interaction with a P2 capsule (persona chat session
         * bootstrap, per-day quota read). Readable only while the capsule is publicly listed
         * (isPublic AND visibilityStatus=PUBLIC) or the requester owns it; tombstone-first,
         * requester state fails closed. Grant-backed, per the CP-30 plan noted on this enum.
         */
        CAPSULE_RUNTIME
    }

    /**
     * Throws UNAUTHORIZED (not owner), FORBIDDEN (requester state fails closed, or a
     * purpose-foreign capsule read) or NOT_FOUND (subject absent or tombstone-blocked —
     * never a hint that it once existed).
     */
    void assertReadable(String subjectType, Long subjectId, Long requesterUserId, Purpose purpose);

    /**
     * CP-14 cache-key contract for portrait/画像 derivatives: any cache that stores a
     * user-portrait derivative compiled from DataUseGrant-backed memories MUST include the
     * grant's authorization version ({@code DataUseGrant.grantVersion}, incremented on every
     * re-authorize/revoke cycle — see {@code DataUseGrantServiceImpl}) in its cache key, so a
     * revoked or re-issued grant necessarily invalidates every cached derivative built from
     * it. Key shape: {@code portrait:<userId>:<grantVersion>} (or equivalent), never
     * {@code portrait:<userId>} alone.
     *
     * <p>Status note (CP-14 wiring pass, honest disclosure): no portrait cache implementation
     * exists in this codebase yet — portrait claims are plain per-request DB reads (see
     * {@code service/portrait/PortraitClaim*Service}). When a portrait cache lands, wire the
     * grant-version dimension into its key at introduction; this guard is the enforcement
     * point at read time, the cache key is the invalidation guarantee.
     */
}
