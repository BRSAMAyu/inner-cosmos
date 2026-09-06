package com.innercosmos.service.identity;

import com.innercosmos.entity.AccountSecurityEvent;
import java.util.List;

/**
 * CP-13 multi-device account security: the owner can see and revoke every device/session
 * trust at once, and trust-and-safety can freeze an account (login is already gated on
 * ACTIVE status). Every action lands in the auditable security-event trail.
 */
public interface AccountSecurityService {

    String STATUS_FROZEN = "FROZEN";

    record SecuritySnapshot(String status, String accountKind, String ageGateMethod,
                            String lastLoginAt, long activeDevices, List<EventRow> events) {
    }

    record EventRow(String action, String actorId, String detail, String createdAt) {
    }

    /** Revoke every active device registration of the user; returns how many were revoked. */
    int revokeAllDevices(Long userId);

    /** Freeze an account (admin action): login and new sessions stop immediately. */
    boolean freeze(Long userId, Long actorId, String reason);

    /** Unfreeze a frozen account. Minor-restricted accounts are not unfreezable here. */
    boolean unfreeze(Long userId, Long actorId, String note);

    SecuritySnapshot snapshot(Long userId);

    List<AccountSecurityEvent> recentEvents(Long userId, int limit);
}
