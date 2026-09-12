package com.innercosmos.service.portrait;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.entity.UserCorrection;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.mapper.UserCorrectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CP-23 owner actions on portrait claims. Deliberately writes its own audit rows instead of
 * going through {@code UserCorrectionService.confirm}: a confirm CREATES a new understanding
 * claim, while these operations change an existing claim's lifecycle — auditing them as
 * corrections would fabricate portrait content.
 */
@Service
public class PortraitClaimControlServiceImpl implements PortraitClaimControlService {

    private final UnderstandingClaimMapper claimMapper;
    private final UserCorrectionMapper correctionMapper;

    public PortraitClaimControlServiceImpl(UnderstandingClaimMapper claimMapper,
                                           UserCorrectionMapper correctionMapper) {
        this.claimMapper = claimMapper;
        this.correctionMapper = correctionMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnderstandingClaim suppress(Long userId, Long claimId, String reason) {
        UnderstandingClaim claim = ownedActive(userId, claimId, "搁置");
        return transition(claim, "SUPPRESSED", "SUPPRESS", reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnderstandingClaim restore(Long userId, Long claimId) {
        UnderstandingClaim claim = owned(userId, claimId);
        if (!"SUPPRESSED".equals(claim.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有被搁置的理解才能恢复");
        }
        return transition(claim, "ACTIVE", "RESTORE", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnderstandingClaim delete(Long userId, Long claimId, String reason) {
        UnderstandingClaim claim = owned(userId, claimId);
        if ("DELETED".equals(claim.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这条理解已被删除");
        }
        return transition(claim, "DELETED", "DELETE", reason);
    }

    private UnderstandingClaim transition(UnderstandingClaim claim, String targetStatus,
                                          String action, String reason) {
        String oldStatus = claim.status;
        claim.status = targetStatus;
        claim.version = (claim.version == null ? 1 : claim.version) + 1;
        claimMapper.updateById(claim);

        UserCorrection audit = new UserCorrection();
        audit.userId = claim.userId;
        audit.targetType = "PORTRAIT_CLAIM";
        audit.targetId = claim.id;
        audit.fieldName = "status";
        audit.oldValue = oldStatus;
        audit.newValue = targetStatus;
        audit.reason = reason == null || reason.isBlank() ? "PORTRAIT_CLAIM_" + action : reason.trim();
        audit.status = "CONFIRMED";
        audit.impactSummary = "PORTRAIT_CLAIM_" + action;
        audit.confirmedAt = LocalDateTime.now();
        correctionMapper.insert(audit);
        return claim;
    }

    private UnderstandingClaim owned(Long userId, Long claimId) {
        // Owner-scoped by construction: a foreign id resolves to "not found", never to
        // another user's row.
        UnderstandingClaim claim = claimMapper.selectOne(new QueryWrapper<UnderstandingClaim>()
                .eq("id", claimId).eq("user_id", userId));
        if (claim == null) throw new BusinessException(ErrorCode.NOT_FOUND, "找不到这条关于你的理解");
        return claim;
    }

    private UnderstandingClaim ownedActive(Long userId, Long claimId, String action) {
        UnderstandingClaim claim = owned(userId, claimId);
        if (!"ACTIVE".equals(claim.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "只有当前有效的理解才能" + action);
        }
        return claim;
    }
}
