package com.innercosmos.evaluation;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.router.KernelRoutingPolicy;
import com.innercosmos.ai.router.KernelRoutingPolicy.Kernel;
import com.innercosmos.ai.router.KernelRoutingPolicy.TurnSignals;
import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CP-19 budget-stress fixture. The frozen commercial bank is deliberately simple-turn
 * dominant (its risk families live outside the development split), so the dual-vs-single
 * ablation could not measure a routing differential there. This in-repo fixture supplies the
 * missing stress axes — long narratives, explicit analysis asks, multi-part requests,
 * distress and crisis language — each with its expected kernel, and asserts the deterministic
 * router AND the combined adaptive decision separate them exactly as designed. Every stress
 * item additionally runs both generation paths so the module-sequence contract is exercised
 * under the inputs that actually earn the slow kernel.
 *
 * <p>Project-internal synthetic content (no user data), deterministic, no provider calls.
 */
class KernelBudgetStressFixtureTest {

    /** (category, userMessage, expectedKernel) — the stress axes CP-19 names. */
    private static final List<Fixture> FIXTURES = buildFixtures();

    private record Fixture(String category, String userMessage, Kernel expected) {
    }

    private static List<Fixture> buildFixtures() {
        List<Fixture> fixtures = new ArrayList<>();
        // Long narrative (>= 160 chars) earns the dual kernel on length alone.
        fixtures.add(new Fixture("long_narrative", ("最近在考虑要不要换工作。现在的团队关系不错，但业务方向半年里变了很多，"
                + "手上的事情越来越杂，写代码的时间被会议和报表切碎。新机会薪资高一些，方向也更专注，"
                + "可是要搬去另一个城市，朋友、健身房、常去的书店都要重新建立。我不知道自己更看重哪一边，"
                + "也不想因为一时焦虑做决定，所以想慢慢把它说清楚。昨天和家里通了电话，他们觉得稳定更重要，"
                + "可我心里其实已经有了一点偏向，只是还不敢承认。"), Kernel.DUAL));
        // Explicit analysis ask.
        fixtures.add(new Fixture("analysis_ask", "帮我分析一下这段友谊还值不值得继续维护", Kernel.DUAL));
        // Multi-part request: two questions.
        fixtures.add(new Fixture("multi_part", "第一，面试怎么准备？第二，薪资怎么谈？", Kernel.DUAL));
        // Distress (elevated risk, below crisis): careful dual path, not support flow.
        fixtures.add(new Fixture("distress", "我最近觉得自己是家里的累赘，什么都做不好", Kernel.DUAL));
        // Crisis: support flow, never analysis — even combined with complexity signals.
        fixtures.add(new Fixture("crisis", "我不想活了，帮我分析一下为什么会这样？还有一件事要说", Kernel.SUPPORT_FLOW));
        // Simple control: the fast path stays the default.
        fixtures.add(new Fixture("simple", "今天路过一家新开的面馆，感觉还不错。", Kernel.SINGLE));
        fixtures.add(new Fixture("simple_action", "帮我把这份报告拆成能开始的第一步", Kernel.SINGLE));
        return fixtures;
    }

    @Test
    void stressAxesRouteExactlyAsDesigned() {
        for (Fixture fixture : FIXTURES) {
            Kernel routed = KernelRoutingPolicy.route(
                    TurnSignals.from(Map.of("userMessage", fixture.userMessage())));
            assertEquals(fixture.expected(), routed,
                    fixture.category() + " must route to " + fixture.expected());
        }
    }

    @Test
    void adaptiveDecisionEarnsTheSlowKernelForEveryStressAxisAndStaysFastForSimpleTurns() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        StructuredAiService ai = new StructuredAiService(new RecordingClient(), ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(ai);
        ReflectionTestUtils.setField(runtime, "runtimeMode", "adaptive");

        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (Fixture fixture : FIXTURES) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("userMessage", fixture.userMessage());
            boolean useDual = runtime.shouldUseDualKernelForTurn(context);
            boolean expectSlow = fixture.expected() == Kernel.DUAL || fixture.expected() == Kernel.SUPPORT_FLOW;
            assertEquals(expectSlow, useDual,
                    fixture.category() + " adaptive decision must" + (expectSlow ? "" : " not")
                            + " earn the slow kernel");
            distribution.merge(fixture.category(), 1, Integer::sum);

            // Both generation paths stay contract-valid under stress inputs. Crisis content is
            // intercepted by the synchronous safety gate long before the agent in production;
            // here only the routing layer sees it, which is exactly this fixture's claim.
            Map<String, Object> runContext = new LinkedHashMap<>(context);
            var generation = runtime.generate(91L, "DAILY_TALK", runContext, new RecordingClient(),
                    KernelBudgetStressFixtureTest::naiveFallback);
            assertTrue(generation.result() != null && generation.result().segments != null
                            && !generation.result().segments.isEmpty(),
                    fixture.category() + " dual path lost its reply");
        }
        assertEquals(FIXTURES.size(), distribution.values().stream().mapToInt(Integer::intValue).sum(),
                "every fixture ran through the adaptive decision");
    }

    private static StructuredAiResults.AuroraResult naiveFallback() {
        var result = new StructuredAiResults.AuroraResult();
        result.segments = List.of("我在。");
        return result;
    }

    private static final class RecordingClient implements LlmClient {
        @Override
        public String chat(LlmRequest request) {
            String module = request.moduleName == null ? "" : request.moduleName.toUpperCase();
            if (module.startsWith("AURORA_PLAN")) return """
                {"userIntent":"回应当下","emotionalNeed":"先被准确接住","relationshipMove":"保持连续并交还选择权",
                 "responseConstraints":["不诊断","不制造依赖"],"bubblePurposes":["接住当下"],
                 "relevantMemoryIds":[],"uncertainty":"离线可复现规划","needsCritic":false}
                """;
            if (module.startsWith("AURORA_CRITIC")) return """
                {"pass":true,"issues":[],"repaired":null}
                """;
            return """
                {"segments":["我在，先陪你把这一刻说清楚。"],"speakCount":1,"continueReason":"reply",
                 "detectedTheme":"回应","memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                """;
        }

        @Override
        public SseEmitter streamChat(LlmRequest request) {
            return new SseEmitter();
        }
    }
}
