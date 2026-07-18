package com.innercosmos.ai.runtime;

import com.innercosmos.ai.context.AgentContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Track A / A1 — pure, deterministic unit tests for {@link DualKernelBudgetPolicy}.
 *
 * <p>No Spring context, no LLM client, no network: every case supplies a hand-built
 * {@link DualKernelBudgetPolicy.Signals} (or a plain turn-context {@link Map} for the
 * {@link DualKernelBudgetPolicy.Signals#from(Map)} extraction path) and asserts the exact
 * {@link DualKernelBudgetPolicy.Budget} the policy returns, per
 * TRACK-A-LIVING-INTELLIGENCE.md §5: "Simple turns should remain fast; high-ambiguity,
 * high-continuity or high-risk turns may spend more budget."
 */
class DualKernelBudgetPolicyTest {

    private final DualKernelBudgetPolicy policy = new DualKernelBudgetPolicy();

    @Test
    void simpleGratitudeMessageStaysSinglePass() {
        var decision = policy.decide(DualKernelBudgetPolicy.Signals.simple(
                "今天只是想说一句谢谢，你在的感觉很好"));

        assertThat(decision.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.SINGLE_PASS);
        assertThat(decision.reasons()).containsExactly("simple_turn:no_risk_ambiguity_or_continuity_signal");
    }

    @Test
    void clearConcreteActionRequestStaysSinglePass() {
        var decision = policy.decide(DualKernelBudgetPolicy.Signals.simple(
                "帮我把这份报告拆成能开始的第一步"));

        assertThat(decision.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.SINGLE_PASS);
    }

    @Test
    void explicitCrisisKeywordForcesDualKernel() {
        var decision = policy.decide(DualKernelBudgetPolicy.Signals.simple(
                "我现在真的不想活下去了"));

        assertThat(decision.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.DUAL_KERNEL);
        assertThat(decision.reasons()).contains("risk:crisis_keyword");
        assertThat(decision.score()).isGreaterThanOrEqualTo(DualKernelBudgetPolicy.DUAL_KERNEL_THRESHOLD);
    }

    @Test
    void implicitDistressSignalWithoutCrisisKeywordAlsoForcesDualKernel() {
        // "我是家里的累赘" hits DistressSignalDetector ("累赘") but no CrisisKeywordRule term.
        var decision = policy.decide(DualKernelBudgetPolicy.Signals.simple("我是家里的累赘"));

        assertThat(decision.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.DUAL_KERNEL);
        assertThat(decision.reasons()).contains("risk:distress_signal");
    }

    @Test
    void ambiguityMarkerAloneForcesDualKernel() {
        var decision = policy.decide(DualKernelBudgetPolicy.Signals.simple(
                "我说不清楚是想要建议还是只想被听见"));

        assertThat(decision.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.DUAL_KERNEL);
        assertThat(decision.reasons()).contains("ambiguity:marker");
    }

    @Test
    void interruptionAloneForcesDualKernel() {
        var decision = policy.decide(new DualKernelBudgetPolicy.Signals(
                "等等，我想先说另一件事", true, 0, 0));

        assertThat(decision.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.DUAL_KERNEL);
        assertThat(decision.reasons()).contains("continuity:interruption");
    }

    @Test
    void singleRelevantMemoryAloneIsNotEnoughForDualKernel() {
        var decision = policy.decide(new DualKernelBudgetPolicy.Signals(
                "顺便提一下这件事", false, 1, 0));

        assertThat(decision.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.SINGLE_PASS);
    }

    @Test
    void twoOrMoreRelevantMemoriesAloneForceDualKernel() {
        var decision = policy.decide(new DualKernelBudgetPolicy.Signals(
                "顺便提一下这件事", false, 2, 0));

        assertThat(decision.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.DUAL_KERNEL);
        assertThat(decision.reasons()).contains("continuity:relevant_memories=2");
    }

    @Test
    void establishedThreadDepthAloneIsNotEnoughButCombinesWithOneMemoryToForceDualKernel() {
        var depthOnly = policy.decide(new DualKernelBudgetPolicy.Signals("接着聊", false, 0, 6));
        assertThat(depthOnly.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.SINGLE_PASS);

        var depthPlusMemory = policy.decide(new DualKernelBudgetPolicy.Signals("接着聊", false, 1, 6));
        assertThat(depthPlusMemory.budget()).isEqualTo(DualKernelBudgetPolicy.Budget.DUAL_KERNEL);
        assertThat(depthPlusMemory.reasons()).contains("continuity:established_thread_depth=6");
    }

    @Test
    void signalsFromMapReadsDirectRelevantMemoryIdsAndInterruptionContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userMessage", "我上次说错了，其实我更喜欢被叫小舟");
        context.put("relevantMemoryIds", List.of(501L, 502L));
        context.put("interruptionContext", "cancel old plan: 讨论工作汇报");

        var signals = DualKernelBudgetPolicy.Signals.from(context);

        assertThat(signals.relevantMemoryCount()).isEqualTo(2);
        assertThat(signals.interruptionPresent()).isTrue();
        assertThat(policy.decide(context).budget()).isEqualTo(DualKernelBudgetPolicy.Budget.DUAL_KERNEL);
    }

    @Test
    void signalsFromMapReadsUnifiedAgentContextWhenDirectKeysAreAbsent() {
        AgentContext agentContext = new AgentContext();
        agentContext.evidenceMemoryIds = List.of(7L, 8L);
        agentContext.recentMessages = List.of("a", "b", "c", "d", "e", "f", "g");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userMessage", "继续吧");
        context.put("unifiedAgentContext", agentContext);

        var signals = DualKernelBudgetPolicy.Signals.from(context);

        assertThat(signals.relevantMemoryCount()).isEqualTo(2);
        assertThat(signals.recentThreadDepth()).isEqualTo(7);
        assertThat(policy.decide(context).budget()).isEqualTo(DualKernelBudgetPolicy.Budget.DUAL_KERNEL);
    }

    @Test
    void nullAndEmptyContextsDegradeSafelyToSinglePass() {
        assertThat(policy.decide((Map<String, Object>) null).budget())
                .isEqualTo(DualKernelBudgetPolicy.Budget.SINGLE_PASS);
        assertThat(policy.decide(Map.of()).budget())
                .isEqualTo(DualKernelBudgetPolicy.Budget.SINGLE_PASS);
    }
}
