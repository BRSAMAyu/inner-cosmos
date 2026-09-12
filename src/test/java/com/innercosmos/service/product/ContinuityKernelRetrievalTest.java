package com.innercosmos.service.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.ai.router.KernelRoutingPolicy;
import com.innercosmos.ai.router.KernelRoutingPolicy.Kernel;
import com.innercosmos.ai.router.KernelRoutingPolicy.TurnSignals;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.DialogSummary;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.DialogSummaryMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.service.UserService;
import com.innercosmos.service.continuity.SessionContinuityService;
import com.innercosmos.service.privacy.RetractionTombstoneService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-18: honest cross-session continuity (fresh user gets zero fabricated references;
 * returning user gets provenance-labeled carry-forward; a 30-day silence is honored).
 * CP-22: withdrawn memories never surface in retrieval — even when a backup restore
 * resurrected their rows as ACTIVE. CP-19: deterministic kernel routing (simple turns stay
 * single-kernel; complexity/risk earn the dual path; crisis routes to support, not analysis).
 */
@SpringBootTest
class ContinuityKernelRetrievalTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private SessionContinuityService continuity;
    @Autowired
    private MemoryRetrievalService retrieval;
    @Autowired
    private RetractionTombstoneService tombstoneService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DialogSessionMapper sessionMapper;
    @Autowired
    private DialogSummaryMapper summaryMapper;
    @Autowired
    private MemoryCardMapper memoryCardMapper;

    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(24).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    @Test
    void cp18_freshUserGetsHonestZeroContinuity() {
        User fresh = human("cp18f");
        var context = continuity.openingContext(fresh.id);
        assertFalse(context.hasPrior());
        assertNull(context.priorSessionId());
        assertTrue(context.carryForward().isEmpty(),
                "no fabricated carry-forward for a first conversation");
        assertTrue(context.openingLine().contains("从头开始"));
    }

    @Test
    void cp18_returningUserGetsProvenanceLabeledCarryForward() {
        User returning = human("cp18r");
        DialogSession prior = new DialogSession();
        prior.userId = returning.id;
        prior.title = "上次的事";
        prior.sessionType = "AURORA_CHAT";
        prior.status = "FINISHED";
        prior.startedAt = LocalDateTime.now(ZoneOffset.UTC).minusDays(2);
        prior.endedAt = LocalDateTime.now(ZoneOffset.UTC).minusDays(2).plusHours(1);
        sessionMapper.insert(prior);

        DialogSummary summary = new DialogSummary();
        summary.sessionId = prior.id;
        summary.userId = returning.id;
        summary.summaryText = "用户谈到工作转向的犹豫，区分了'不想做'和'怕做不好'。";
        summary.keyTopics = "职业转换,自我评价";
        summary.emotionTone = "审慎";
        summary.messageCountAtSummary = 12;
        summaryMapper.insert(summary);

        var context = continuity.openingContext(returning.id);
        assertTrue(context.hasPrior());
        assertEquals(prior.id, context.priorSessionId());
        assertTrue(context.carryForward().stream()
                .anyMatch(note -> "PRIOR_SUMMARY".equals(note.kind())
                        && note.provenance().contains("上次对话")));
        assertTrue(context.carryForward().stream()
                .anyMatch(note -> "PRIOR_TOPICS".equals(note.kind())
                        && note.text().contains("职业转换")));
        assertTrue(context.openingLine().contains("聊过一次"));

        // A silence longer than the 30-day window is honored: honest fresh start again.
        sessionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update
                .UpdateWrapper<DialogSession>().eq("id", prior.id)
                .set("ended_at", LocalDateTime.now(ZoneOffset.UTC).minusDays(45)));
        var afterSilence = continuity.openingContext(returning.id);
        assertFalse(afterSilence.hasPrior());
        assertTrue(afterSilence.carryForward().isEmpty());
    }

    @Test
    void cp22_withdrawnMemoriesNeverSurfaceEvenAfterBackupResurrection() {
        User owner = human("cp22");
        MemoryCard withdrawn = memory(owner, "已撤回的记忆内容");
        MemoryCard healthy = memory(owner, "健康的记忆内容");
        tombstoneService.record("MEMORY", withdrawn.id, owner.id, null, "owner forget");
        // Backup resurrection: the withdrawn row comes back ACTIVE.
        withdrawn.status = "ACTIVE";
        memoryCardMapper.updateById(withdrawn);

        var pack = retrieval.retrieve(owner.id,
                new com.innercosmos.dto.MemoryRetrievalQuery(
                        "记忆内容", null, null, null, null, false));
        var ids = pack.evidence().stream().map(com.innercosmos.vo.MemoryEvidencePackVO.Evidence::memoryId).toList();
        assertTrue(ids.contains(healthy.id));
        assertFalse(ids.contains(withdrawn.id),
                "a withdrawn memory must never be retrieved, even resurrected ACTIVE");
    }

    @Test
    void cp19_kernelRoutingIsDeterministicComplexityAndRiskBased() {
        // Simple short turn: single fast kernel — extra stages are never the default.
        assertEquals(Kernel.SINGLE, KernelRoutingPolicy.route(
                new TurnSignals(20, false, false, false, false)));
        // Complexity earns the dual path: length, explicit analysis ask, or multi-part.
        assertEquals(Kernel.DUAL, KernelRoutingPolicy.route(
                new TurnSignals(200, false, false, false, false)));
        assertEquals(Kernel.DUAL, KernelRoutingPolicy.route(
                new TurnSignals(20, true, false, false, false)));
        assertEquals(Kernel.DUAL, KernelRoutingPolicy.route(
                new TurnSignals(20, false, true, false, false)));
        // Elevated risk context: careful dual path with critic oversight.
        assertEquals(Kernel.DUAL, KernelRoutingPolicy.route(
                new TurnSignals(20, false, false, true, false)));
        // Crisis NEVER routes to deep analysis — support flow, immediately.
        assertEquals(Kernel.SUPPORT_FLOW, KernelRoutingPolicy.route(
                new TurnSignals(500, true, true, true, true)));
        assertEquals(Kernel.SUPPORT_FLOW, KernelRoutingPolicy.route(
                new TurnSignals(10, false, false, false, true)));
        // Null signals fail safe to the simple path.
        assertEquals(Kernel.SINGLE, KernelRoutingPolicy.route(null));
    }

    private MemoryCard memory(User owner, String title) {
        MemoryCard card = new MemoryCard();
        card.userId = owner.id;
        card.title = title;
        card.memoryType = "FACT";
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.emotionalGravity = 0.4;
        memoryCardMapper.insert(card);
        return card;
    }
}
