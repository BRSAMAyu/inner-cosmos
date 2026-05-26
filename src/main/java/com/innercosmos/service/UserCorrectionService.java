package com.innercosmos.service;

import com.innercosmos.entity.UserCorrection;

import java.util.List;

public interface UserCorrectionService {
    UserCorrection recordCorrection(Long userId, String targetType, Long targetId,
                                     String fieldName, String oldValue, String newValue, String reason);

    List<UserCorrection> listCorrections(Long userId, String targetType, Long targetId);
}
