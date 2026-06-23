package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.UserCorrection;
import com.innercosmos.mapper.UserCorrectionMapper;
import com.innercosmos.service.UserCorrectionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserCorrectionServiceImpl implements UserCorrectionService {
    private final UserCorrectionMapper userCorrectionMapper;

    public UserCorrectionServiceImpl(UserCorrectionMapper userCorrectionMapper) {
        this.userCorrectionMapper = userCorrectionMapper;
    }

    @Override
    public UserCorrection recordCorrection(Long userId, String targetType, Long targetId,
                                            String fieldName, String oldValue, String newValue, String reason) {
        UserCorrection correction = new UserCorrection();
        correction.userId = userId;
        correction.targetType = targetType;
        correction.targetId = targetId;
        correction.fieldName = fieldName;
        correction.oldValue = oldValue;
        correction.newValue = newValue;
        correction.reason = reason;
        userCorrectionMapper.insert(correction);
        return correction;
    }

    @Override
    public List<UserCorrection> listCorrections(Long userId, String targetType, Long targetId) {
        QueryWrapper<UserCorrection> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        if (targetType != null) {
            query.eq("target_type", targetType);
        }
        if (targetId != null) {
            query.eq("target_id", targetId);
        }
        query.orderByDesc("id");
        return userCorrectionMapper.selectList(query);
    }

    @Override
    public List<UserCorrection> recentCorrections(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        QueryWrapper<UserCorrection> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("id").last("LIMIT " + limit);
        return userCorrectionMapper.selectList(query);
    }

    @Override
    public List<UserCorrection> recentCorrectionsByType(Long userId, String targetType, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        QueryWrapper<UserCorrection> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        if (targetType != null && !targetType.isBlank()) {
            query.eq("target_type", targetType);
        }
        query.orderByDesc("id").last("LIMIT " + limit);
        return userCorrectionMapper.selectList(query);
    }
}
