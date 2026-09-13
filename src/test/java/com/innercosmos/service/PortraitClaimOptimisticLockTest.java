package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserCorrection;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.mapper.UserCorrectionMapper;
import com.innercosmos.service.portrait.PortraitClaimControlService;
import com.innercosmos.service.portrait.PortraitClaimViewService;
import com.innercosmos.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CP-21 backend half for portrait claims: suppress / restore / delete are transitions on an
 * existing claim row, so they now take {@code expectedVersion} — the {@code version} the caller
 * last saw (the same value {@code PortraitClaimViewService.ClaimView#version()} renders). A
 * stale pin dies as {@link ErrorCode#CONFLICT} (the only channel the web client's
 * {@code isVersionConflictError} recognizes) with the row and its audit trail untouched; a
 * fresh pin lands and bumps the version by one. Legacy callers without a pin keep working.
 */
@SpringBootTest
class PortraitClaimOptimisticLockTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired PortraitClaimControlService claimControl;
    @Autowired PortraitClaimViewService portraitView;
    @Autowired UserService userService;
    @Autowired UnderstandingClaimMapper claimMapper;
    @Autowired UserCorrectionMapper correctionMapper;

    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(26).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    private UnderstandingClaim inferred(User owner, String key, String value) {
        UnderstandingClaim claim = new UnderstandingClaim();
        claim.userId = owner.id;
        claim.claimKey = key;
        claim.claimType = "PORTRAIT_DIM";
        claim.valueJson = "\"" + value + "\"";
        claim.authorityLevel = "MODEL_INFERENCE";
        claim.confidence = 0.7;
        claim.status = "ACTIVE";
        claim.sourceType = "AUTO_EXTRACTION";
        claim.version = 1;
        claimMapper.insert(claim);
        return claim;
    }

    private long auditCount(Long claimId) {
        return correctionMapper.selectCount(new QueryWrapper<UserCorrection>()
                .eq("target_type", "PORTRAIT_CLAIM").eq("target_id", claimId));
    }

    @Test
    void staleExpectedVersionIsRejectedAsConflictAndTouchesNothing() {
        User owner = human("cp21a");
        UnderstandingClaim claim = inferred(owner, "表达习惯", "喜欢长段落自我分析");

        // Someone else's transition lands first: 1 -> 2.
        claimControl.suppress(owner.id, claim.id, "先到的人", 1);

        // The stale caller (still pinning the version it rendered) must NOT overwrite it.
        assertThatThrownBy(() -> claimControl.delete(owner.id, claim.id, "后到的人", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被他人更新")
                .extracting(e -> ((BusinessException) e).code)
                .isEqualTo(ErrorCode.CONFLICT);

        UnderstandingClaim row = claimMapper.selectById(claim.id);
        assertThat(row.status).isEqualTo("SUPPRESSED");   // the first writer's state stands
        assertThat(row.version).isEqualTo(2);              // no second bump, no lost update
        assertThat(auditCount(claim.id)).isEqualTo(1);     // no fabricated audit for the loser

        // The view still exposes the row's real version, so the client can re-pin and retry.
        var suppressedRow = portraitView.view(owner.id).suppressed().stream()
                .filter(r -> claim.id == r.claimId()).findFirst().orElseThrow();
        assertThat(suppressedRow.version()).isEqualTo("2");
        var deleted = claimControl.delete(owner.id, claim.id, "基于最新版本重做", 2);
        assertThat(deleted.status).isEqualTo("DELETED");
        assertThat(deleted.version).isEqualTo(3);
    }

    @Test
    void freshExpectedVersionLandsAndBumpsTheVersionByOne() {
        User owner = human("cp21b");
        UnderstandingClaim claim = inferred(owner, "支持偏好", "需要具体的行动建议");

        var suppressed = claimControl.suppress(owner.id, claim.id, "这不太是我", 1);
        assertThat(suppressed.status).isEqualTo("SUPPRESSED");
        assertThat(suppressed.version).isEqualTo(2);
        assertThat(claimMapper.selectById(claim.id).version).isEqualTo(2);

        var restored = claimControl.restore(owner.id, claim.id, 2);
        assertThat(restored.status).isEqualTo("ACTIVE");
        assertThat(restored.version).isEqualTo(3);

        var deleted = claimControl.delete(owner.id, claim.id, "不想保留", 3);
        assertThat(deleted.status).isEqualTo("DELETED");
        assertThat(deleted.version).isEqualTo(4);
        assertThat(auditCount(claim.id)).isEqualTo(3);
    }

    @Test
    void legacyCallerWithoutExpectedVersionStillWorksAndCannotSilentlyClobber() {
        User owner = human("cp21c");
        UnderstandingClaim claim = inferred(owner, "关系节律", "一周一次深谈就够了");

        // Legacy path (no pin) keeps working — the CP-23 flows and direct tests stay green.
        var suppressed = claimControl.suppress(owner.id, claim.id, null, null);
        assertThat(suppressed.version).isEqualTo(2);

        // But a stale pin against the legacy result is still a conflict, not an overwrite:
        // even a caller that never looked at the version cannot re-run against version 1.
        assertThatThrownBy(() -> claimControl.restore(owner.id, claim.id, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code)
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(claimMapper.selectById(claim.id).status).isEqualTo("SUPPRESSED");

        var restored = claimControl.restore(owner.id, claim.id, null);
        assertThat(restored.status).isEqualTo("ACTIVE");
        assertThat(restored.version).isEqualTo(3);
    }

    @Test
    void conflictPinBeatsTheStatusGuardDifferenceSoWrongKeyCannotBypass() {
        User owner = human("cp21d");
        UnderstandingClaim claim = inferred(owner, "价值偏好", "诚实优先于舒适");

        // suppress requires ACTIVE; the row IS active, but the pinned version is wrong —
        // the version gate must fire (CONFLICT), not be bypassed by the status guard passing.
        assertThatThrownBy(() -> claimControl.suppress(owner.id, claim.id, "理由", 7))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code)
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(claimMapper.selectById(claim.id).status).isEqualTo("ACTIVE");
        assertThat(auditCount(claim.id)).isZero();
    }
}
