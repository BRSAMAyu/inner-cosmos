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

    /** Consumer read purposes. CAPSULE_RUNTIME (P2, grant-backed) lands with CP-30. */
    enum Purpose { OWNER_READ }

    /**
     * Throws UNAUTHORIZED (not owner), FORBIDDEN (requester state fails closed) or
     * NOT_FOUND (subject absent or tombstone-blocked — never a hint that it once existed).
     */
    void assertReadable(String subjectType, Long subjectId, Long requesterUserId, Purpose purpose);
}
