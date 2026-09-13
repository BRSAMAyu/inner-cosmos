package com.innercosmos.service.portrait;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
 *
 * <p>CP-21 optimistic concurrency: {@code version} here is the claim row's lineage/lifecycle
 * counter (unique per user_id+claim_key in {@code uk_understanding_claim_version}). It is the
 * token {@code PortraitClaimViewService} already renders, and every lifecycle transition bumps
 * it — the correction-confirm flow only SUPERSEDES an old row without touching its version, so
 * a row's {@code version} moves on exactly the paths this class guards. The transition is a
 * single conditional UPDATE gated on id + owner + the expected version, so a racing transition
 * (this client's belief is stale, or a legacy caller with no expectation at all) surfaces as
 * {@link ErrorCode#CONFLICT} instead of silently overwriting the other writer.</p>
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
    public UnderstandingClaim suppress(Long userId, Long claimId, String reason, Integer expectedVersion) {
        UnderstandingClaim claim = ownedActive(userId, claimId, "搁置");
        return transition(claim, "SUPPRESSED", "SUPPRESS", reason, expectedVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnderstandingClaim restore(Long userId, Long claimId, Integer expectedVersion) {
        UnderstandingClaim claim = owned(userId, claimId);
        if (!"SUPPRESSED".equals(claim.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有被搁置的理解才能恢复");
        }
        return transition(claim, "ACTIVE", "RESTORE", null, expectedVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnderstandingClaim delete(Long userId, Long claimId, String reason, Integer expectedVersion) {
        UnderstandingClaim claim = owned(userId, claimId);
        if ("DELETED".equals(claim.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这条理解已被删除");
        }
        return transition(claim, "DELETED", "DELETE", reason, expectedVersion);
    }

    private UnderstandingClaim transition(UnderstandingClaim claim, String targetStatus,
                                          String action, String reason, Integer expectedVersion) {
        String oldStatus = claim.status;
        int currentVersion = claim.version == null ? 1 : claim.version;
        // CP-21: a caller that rendered the claim pins the version it saw. A stale pin is the
        // "someone updated this before you" case — 409 CONFLICT, no data touched, no bypass
        // that lets a stale caller overwrite the newer write.
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "这条理解在你操作前已被他人更新（当前版本 " + currentVersion
                            + "，你基于版本 " + expectedVersion + "），请查看最新后再试");
        }
        // The transition itself is one atomic conditional UPDATE on the expected version, so
        // even between the read above and this write a racing transition cannot be lost — it
        // makes rowsAffected 0 and surfaces as CONFLICT for legacy callers too.
        int nextVersion = currentVersion + 1;
        int updated = claimMapper.update(null, new UpdateWrapper<UnderstandingClaim>()
                .eq("id", claim.id)
                .eq("user_id", claim.userId)
                .eq("version", currentVersion)
                .set("status", targetStatus)
                .set("version", nextVersion)
                .set("updated_at", LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "这条理解在你操作前已被他人更新，请查看最新后再试");
        }
        claim.status = targetStatus;
        claim.version = nextVersion;

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
