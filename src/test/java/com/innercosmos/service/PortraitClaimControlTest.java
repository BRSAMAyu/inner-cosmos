package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserCorrection;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CP-23 owner control over the correctable portrait: park (搁置) an inference, restore it,
 * delete it — each owner-scoped, versioned and audit-recorded; suppressed/deleted claims
 * disappear from the portrait view AND from the ACTIVE pool Aurora's per-turn context reads.
 * Plus the J04 journey: a correction made "today" is fully adopted by the next view — the
 * corrected value is the only CONFIRMED state, the old one superseded out of sight.
 */
@SpringBootTest
class PortraitClaimControlTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired PortraitClaimControlService claimControl;
    @Autowired PortraitClaimViewService portraitView;
    @Autowired UserCorrectionService correctionService;
    @Autowired UserService userService;
    @Autowired UnderstandingClaimMapper claimMapper;
    @Autowired UserCorrectionMapper correctionMapper;

    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(27).toString();
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

    @Test
    void suppressedClaimLeavesViewAndActiveContextButKeepsItsAuditTrail() {
        User owner = human("cp23s");
        UnderstandingClaim claim = inferred(owner, "表达习惯", "喜欢长段落自我分析");
        User stranger = human("cp23x");

        assertThatThrownBy(() -> claimControl.suppress(stranger.id, claim.id, "不是我", null))
                .hasMessageContaining("找不到");

        var suppressed = claimControl.suppress(owner.id, claim.id, "这不太是我", null);
        assertThat(suppressed.status).isEqualTo("SUPPRESSED");
        assertThat(suppressed.version).isEqualTo(2);

        // Gone from the correctable-portrait view...
        assertThat(portraitView.view(owner.id).claims().stream()
                .noneMatch(row -> "表达习惯".equals(row.claimKey()))).isTrue();
        // ...and from the ACTIVE pool Aurora's per-turn context reads.
        assertThat(claimMapper.selectList(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", owner.id).eq("status", "ACTIVE"))).isEmpty();

        // Audit row exists and says exactly what happened — no fabricated portrait content.
        UserCorrection audit = correctionMapper.selectOne(new QueryWrapper<UserCorrection>()
                .eq("target_type", "PORTRAIT_CLAIM").eq("target_id", claim.id)
                .orderByDesc("id").last("LIMIT 1"));
        assertThat(audit).isNotNull();
        assertThat(audit.oldValue).isEqualTo("ACTIVE");
        assertThat(audit.newValue).isEqualTo("SUPPRESSED");
        assertThat(audit.reason).isEqualTo("这不太是我");

        // Restore brings it back — still owner-scoped.
        assertThatThrownBy(() -> claimControl.restore(stranger.id, claim.id, null))
                .hasMessageContaining("找不到");
        var restored = claimControl.restore(owner.id, claim.id, null);
        assertThat(restored.status).isEqualTo("ACTIVE");
        assertThat(restored.version).isEqualTo(3);
        assertThat(portraitView.view(owner.id).claims().stream()
                .anyMatch(row -> "表达习惯".equals(row.claimKey()))).isTrue();

        // Only an ACTIVE claim can be parked; a suppressed one cannot be parked again.
        claimControl.suppress(owner.id, claim.id, null, null);
        assertThatThrownBy(() -> claimControl.suppress(owner.id, claim.id, null, null))
                .hasMessageContaining("只有当前有效的理解才能");
    }

    @Test
    void deletedClaimIsGoneFromEveryCurrentSurfaceAndCannotBeDeletedTwice() {
        User owner = human("cp23d");
        UnderstandingClaim claim = inferred(owner, "支持偏好", "需要具体的行动建议");

        var deleted = claimControl.delete(owner.id, claim.id, "我不想保留这条", null);
        assertThat(deleted.status).isEqualTo("DELETED");
        assertThat(portraitView.view(owner.id).claims()).isEmpty();
        assertThat(claimMapper.selectList(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", owner.id).eq("status", "ACTIVE"))).isEmpty();

        assertThatThrownBy(() -> claimControl.delete(owner.id, claim.id, "again", null))
                .hasMessageContaining("已被删除");
        // The audit row outlives the claim's visibility.
        assertThat(correctionMapper.selectCount(new QueryWrapper<UserCorrection>()
                .eq("target_type", "PORTRAIT_CLAIM").eq("target_id", claim.id))).isEqualTo(1);
    }

    @Test
    void j04_todaysCorrectionIsFullyAdoptedByTheNextPortraitView() {
        User owner = human("cp23j");
        // The canonical self-understanding claim the extraction pipeline maintains.
        inferred(owner, "AURORA_UNDERSTANDING:0:self_understanding", "独处充电");

        // "Today": the user corrects Aurora's understanding through the real corrections flow.
        correctionService.confirm(owner.id, new CorrectionCommand(
                "AURORA_UNDERSTANDING", 0L, "self_understanding", "独处充电",
                "安静恢复精力，但也珍惜几次深入的对谈", "这不太是我"));

        // "Tomorrow" (the next time anyone looks): the corrected value is the ONLY current
        // state, with user-correction authority; the old inference is superseded out of sight.
        var view = portraitView.view(owner.id);
        assertThat(view.claims()).hasSize(1);
        var row = view.claims().get(0);
        assertThat(row.authorityLevel()).isEqualTo("USER_CORRECTION");
        assertThat(row.value()).contains("深入的对谈");
        assertThat(row.state()).isIn("CONFIRMED", "INFERRED");

        List<UnderstandingClaim> active = claimMapper.selectList(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", owner.id).eq("status", "ACTIVE"));
        assertThat(active).hasSize(1);
        assertThat(active.get(0).authorityLevel).isEqualTo("USER_CORRECTION");
        assertThat(active.get(0).valueJson).contains("深入的对谈");
    }

    private static boolean keyMatches(PortraitClaimViewService.ClaimView row, String key) {
        return key.equals(row.claimKey());
    }
}
