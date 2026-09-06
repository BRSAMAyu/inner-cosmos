package com.innercosmos.service.minor;

import com.innercosmos.entity.MinorAppeal;
import java.util.List;

/**
 * CP-08 minor-intercept lifecycle. Blueprint §5.2: once identified as a minor, the account
 * stops receiving the adult companion/social service but keeps safety resources, an
 * explanation and a working appeal path; a misjudged adult must be restorable.
 */
public interface MinorProtectionService {

    /** Status value moved onto the user row when a minor is identified. */
    String STATUS_MINOR_RESTRICTED = "MINOR_RESTRICTED";

    /**
     * Entry guard for adult companion/chat surfaces. Throws ADULT_GATE_REQUIRED with the
     * restriction explanation and appeal pointer for restricted accounts.
     */
    void assertAdultAccess(Long userId);

    /**
     * Move an account into MINOR_RESTRICTED (admin/trust-and-safety action).
     *
     * @return true when the transition happened, false when already restricted.
     */
    boolean flagMinor(Long userId, String reason);

    /** File an appeal from a restricted account; only one PENDING appeal at a time. */
    MinorAppeal appeal(Long userId, String statement);

    /** Decide a pending appeal; ACCEPTED restores the account to ACTIVE. */
    MinorAppeal resolve(Long appealId, boolean accept, Long adminUserId, String note);

    List<MinorAppeal> pendingAppeals();
}
