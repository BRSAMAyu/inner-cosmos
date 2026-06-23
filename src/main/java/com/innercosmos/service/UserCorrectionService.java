package com.innercosmos.service;

import com.innercosmos.entity.UserCorrection;

import java.util.List;

public interface UserCorrectionService {
    UserCorrection recordCorrection(Long userId, String targetType, Long targetId,
                                     String fieldName, String oldValue, String newValue, String reason);

    List<UserCorrection> listCorrections(Long userId, String targetType, Long targetId);

    /**
     * RUN-005 — the N most recent corrections this user made, newest first. Feeds the
     * Aurora prompt so the model visibly adapts after a "这不太是我" correction.
     */
    List<UserCorrection> recentCorrections(Long userId, int limit);

    /**
     * RUN-006 — the N most recent corrections of a specific {@code targetType}, newest
     * first. Lets the Aurora prompt route authoritative free-form corrections
     * (AURORA_UNDERSTANDING) and soft portrait calibrations (PORTRAIT_DIM) into their
     * own blocks with different precedence. A null/blank targetType behaves like
     * {@link #recentCorrections(Long, int)}.
     */
    List<UserCorrection> recentCorrectionsByType(Long userId, String targetType, int limit);

    /**
     * M-069 — retire (hard-delete) one of the user's own corrections. Loads by id and
     * throws UNAUTHORIZED if the correction does not exist or belongs to another user,
     * so a forged id can never touch someone else's data.
     */
    void deleteCorrection(Long userId, Long id);
}
