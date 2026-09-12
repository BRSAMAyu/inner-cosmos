package com.innercosmos.service.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.ai.router.KernelRoutingPolicy;
import com.innercosmos.ai.router.KernelRoutingPolicy.TurnSignals;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.DialogSummary;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.DialogSummaryMapper;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.DialogService;
import com.innercosmos.service.UserService;
import com.innercosmos.service.impl.AuroraAgentServiceImpl;
import com.innercosmos.vo.AuroraReplyVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-18/CP-19 production wiring, asserted through the real reply path (mock provider):
 * the deterministic kernel route is visible on every turn, complexity earns the DUAL route,
 * the opening turn of a fresh session carries exactly the provenance-labeled carry-forward
 * (diagnostics see a count, never the text), later turns carry none of it, and a brand-new
 * user gets the explicit first-conversation guard instead of fabricated continuity.
 */
@SpringBootTest
class AuroraContinuityRoutingWiringTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private AuroraAgentService aurora;
    @Autowired
    private DialogService dialogService;
    @Autowired
    private UserService userService;
    @Autowired
    private DialogSessionMapper sessionMapper;
    @Autowired
    private DialogSummaryMapper summaryMapper;

    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(25).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    private DialogSession newSession(User user) {
        SessionCreateRequest create = new SessionCreateRequest();
        create.title = "今晚";
        return dialogService.create(user.id, create);
    }

    private AuroraReplyVO say(User user, DialogSession session, String message) {
        ChatRequest request = new ChatRequest();
        request.sessionId = session.id;
        request.message = message;
        return aurora.replyRich(user.id, request);
    }

    @Test
    void turnSignalsFromMapExtractsAllFiveProductionSignals() {
        TurnSignals none = TurnSignals.from(null);
        assertEquals(0, none.inputLength());
        assertFalse(none.userAskedForAnalysis());
        assertFalse(none.multiPartRequest());
        assertFalse(none.riskContext());
        assertFalse(none.crisisHit());

        TurnSignals simple = TurnSignals.from(Map.of("userMessage", "今天路过一家新开的面馆，感觉还不错。"));
        assertTrue(simple.inputLength() > 0);
        assertFalse(simple.userAskedForAnalysis());
        assertFalse(simple.multiPartRequest());
        assertFalse(simple.riskContext());
        assertFalse(simple.crisisHit());

        assertTrue(TurnSignals.from(Map.of("userMessage", "帮我分析一下这份工作该不该接"))
                .userAskedForAnalysis());
        assertTrue(TurnSignals.from(Map.of("userMessage", "第一，面试怎么准备？第二，薪资怎么谈？"))
                .multiPartRequest());
        assertTrue(TurnSignals.from(Map.of("userMessage", "我最近觉得自己是家里的累赘"))
                .riskContext());
        assertTrue(TurnSignals.from(Map.of("userMessage", "我不想活了")).crisisHit());
    }

    @Test
    void kernelRouteVisibleOnEveryRealTurnAndComplexityEarnsDual() {
        User user = human("cp19w");
        DialogSession session = newSession(user);

        AuroraReplyVO simple = say(user, session, "嗯，刚到家。");
        assertEquals("SINGLE", simple.agentLoop.get("kernelRoute"));

        String longAnalysis = "最近在考虑要不要换工作，帮我分析一下。"
                + "现在的团队关系不错，但业务方向过去半年变了很多，手上的事情越来越杂。"
                + "新机会薪资高一些，方向也更专注，可是要搬去另一个城市，朋友和习惯都要重新建立。"
                + "我不知道自己更看重哪一边，也不想因为一时焦虑做决定。";
        AuroraReplyVO complex = say(user, session, longAnalysis);
        assertEquals("DUAL", complex.agentLoop.get("kernelRoute"));
        // CP-18: the second turn of the same session carries no cross-session material.
        assertFalse(complex.agentLoop.containsKey("crossSessionContinuityCarry"));
    }

    @Test
    void openingTurnCarriesCarryCountLaterTurnsDoNot() {
        User returning = human("cp18w");
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

        DialogSession fresh = newSession(returning);
        AuroraReplyVO opening = say(returning, fresh, "我回来了，想接着上次的聊。");
        assertEquals(2, opening.agentLoop.get("crossSessionContinuityCarry"),
                "the opening turn carries both PRIOR_SUMMARY and PRIOR_TOPICS as a count");
        assertFalse(opening.agentLoop.containsKey("firstConversationGuard"));
        // The diagnostic value must be a count, never the carry text itself.
        assertFalse(String.valueOf(opening.agentLoop.get("crossSessionContinuityCarry"))
                .contains("职业转换"));

        AuroraReplyVO later = say(returning, fresh, "其实我今天想说的是另一件事。");
        assertFalse(later.agentLoop.containsKey("crossSessionContinuityCarry"),
                "live history replaces cross-session material after the opening turn");
    }

    @Test
    void freshUserFirstTurnGetsFirstConversationGuardNotFabricatedContinuity() {
        User fresh = human("cp18g");
        DialogSession session = newSession(fresh);
        AuroraReplyVO opening = say(fresh, session, "在吗？想随便聊聊。");
        assertEquals(Boolean.TRUE, opening.agentLoop.get("firstConversationGuard"));
        assertFalse(opening.agentLoop.containsKey("crossSessionContinuityCarry"));

        AuroraReplyVO later = say(fresh, session, "今天有点累，不过还好。");
        assertFalse(later.agentLoop.containsKey("firstConversationGuard"),
                "the guard belongs to the opening turn only");
    }

    @Test
    void continuityGroundingIsDataOnlyAndHonestAboutAllThreeStates() {
        // Returning user with material: provenance-labeled carry notes, no instructions.
        var carry = List.of(
                new com.innercosmos.service.continuity.SessionContinuityService.CarryNote(
                        "PRIOR_SUMMARY", "上次谈到了职业犹豫", "上次对话（9月10日）的整理"),
                new com.innercosmos.service.continuity.SessionContinuityService.CarryNote(
                        "PRIOR_TOPICS", "职业转换", "上次对话（9月10日）的整理"));
        var grounding = AuroraAgentServiceImpl.continuityGrounding(
                new com.innercosmos.service.continuity.SessionContinuityService.OpeningContext(
                        true, 7L, "2026-09-10", carry, "你聊过一次。"));
        assertTrue(grounding.containsKey("carryForward"));
        assertTrue(grounding.containsKey("priorActiveAt"));
        assertFalse(grounding.containsKey("crossSessionContinuityFirstConversation"));

        // Brand-new user: the guard, never a fabricated prior.
        var first = AuroraAgentServiceImpl.continuityGrounding(
                new com.innercosmos.service.continuity.SessionContinuityService.OpeningContext(
                        false, null, null, List.of(), "从头开始。"));
        assertEquals(Map.of("crossSessionContinuityFirstConversation", true), first);

        // Prior exists but nothing survived: neither fabrication nor a false "first" claim.
        var silent = AuroraAgentServiceImpl.continuityGrounding(
                new com.innercosmos.service.continuity.SessionContinuityService.OpeningContext(
                        true, 7L, "2026-09-10", List.of(), "我们从头开始。"));
        assertEquals(Map.of(), silent);

        assertEquals(Map.of(), AuroraAgentServiceImpl.continuityGrounding(null));
    }

    @Test
    void crisisLanguageNeverReachesAnAnalysisTurnInTheRealPath() {
        User user = human("cp19s");
        DialogSession session = newSession(user);
        // Crisis language is stopped by the synchronous safety gate BEFORE the agent: the
        // reply is a support-first safety reply, and no kernel route is ever produced for it.
        // The SUPPORT_FLOW route itself (crisis that somehow slipped past the gate) is
        // deterministically unit-covered in ContinuityKernelRetrievalTest.
        ChatRequest request = new ChatRequest();
        request.sessionId = session.id;
        request.message = "我不想活了";
        AuroraReplyVO reply = aurora.replyRich(user.id, request);
        assertEquals("SUPPORT_FLOW", KernelRoutingPolicy.route(
                TurnSignals.from(Map.of("userMessage", request.message))).name());
        assertTrue(reply.agentLoop == null || !reply.agentLoop.containsKey("kernelRoute"),
                "a blocked crisis turn never reaches kernel routing / analysis");
        assertTrue(reply.riskFlags != null && !reply.riskFlags.isEmpty());
    }
}
