package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.UserCorrection;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.UserCorrectionMapper;
import com.innercosmos.service.UserCorrectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserCorrectionServiceImpl implements UserCorrectionService {
    private static final Logger log = LoggerFactory.getLogger(UserCorrectionServiceImpl.class);
    private final UserCorrectionMapper userCorrectionMapper;
    private final UserPortraitService userPortraitService;

    public UserCorrectionServiceImpl(UserCorrectionMapper userCorrectionMapper,
                                     UserPortraitService userPortraitService) {
        this.userCorrectionMapper = userCorrectionMapper;
        this.userPortraitService = userPortraitService;
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
        // M-017: a PORTRAIT_DIM correction ("Aurora 眼中的你" calibration) durably reshapes the
        // portrait via applyDeltas — otherwise the "这不太是我" loop was prompt-only and forgot
        // once the correction aged out of the prompt window.
        if ("PORTRAIT_DIM".equals(targetType) && fieldName != null && newValue != null) {
            try {
                PortraitDeltas.Delta delta = new PortraitDeltas.Delta(
                        fieldName, jsonEncode(newValue), 1.0, 0.95, List.of("user_correction"));
                userPortraitService.applyDeltas(userId, List.of(delta));
            } catch (Exception e) {
                log.warn("Applying PORTRAIT_DIM correction to portrait failed for user {}: {}", userId, e.getMessage());
            }
        }
        return correction;
    }

    private static String jsonEncode(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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

    @Override
    public void deleteCorrection(Long userId, Long id) {
        if (userId == null || id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权操作该更正");
        }
        UserCorrection correction = userCorrectionMapper.selectById(id);
        if (correction == null || !userId.equals(correction.userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权操作该更正");
        }
        userCorrectionMapper.deleteById(id);
    }
}
