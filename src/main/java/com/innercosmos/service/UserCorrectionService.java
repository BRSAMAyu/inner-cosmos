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
}
