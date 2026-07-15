package com.innercosmos.ai.runtime;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Campaign A runtime: a compact understanding/planning kernel followed by a separate
 * relationship/expression kernel, with a bounded critic only when the plan says risk
 * or the generated response violates observable quality constraints.
 */
@Component
public class AuroraDualKernelRuntime {
    private final StructuredAiService ai;

    @Value("${inner-cosmos.aurora.runtime:dual}")
    private String runtimeMode = "dual";

    public AuroraDualKernelRuntime(StructuredAiService ai) {
        this.ai = ai;
    }

    public boolean enabled() {
        return !"single".equalsIgnoreCase(runtimeMode);
    }

    public Generation generate(Long userId, String mode, Map<String, Object> assembledContext,
                               LlmClient client, Supplier<StructuredAiResults.AuroraResult> fallback) {
        if (!enabled()) return new Generation(fallback.get(), "single-fallback", "", false, List.of());

        var plan = ai.call(userId, "AURORA_PLAN_" + mode, planInstruction(), assembledContext,
            StructuredAiResults.AuroraPlanResult.class, () -> fallbackPlan(assembledContext), client);
        normalizePlan(plan, assembledContext);

        Map<String, Object> speakerContext = new LinkedHashMap<>(assembledContext);
        speakerContext.remove("auroraPrompt"); // planner output replaces the former monolithic prompt
        speakerContext.put("responsePlan", plan);
        speakerContext.put("runtimeContract", "dual-kernel.v1");
        var spoken = ai.call(userId, "AURORA_SPEAKER_" + mode, speakerInstruction(), speakerContext,
            StructuredAiResults.AuroraResult.class, fallback, client);

        List<String> observableIssues = qualityIssues(spoken, plan);
        boolean criticRequested = Boolean.TRUE.equals(plan.needsCritic) || !observableIssues.isEmpty();
        boolean repaired = false;
        if (criticRequested) {
            Map<String, Object> criticContext = new LinkedHashMap<>();
            criticContext.put("plan", plan);
            criticContext.put("candidate", spoken);
            criticContext.put("observableIssues", observableIssues);
            criticContext.put("userInput", assembledContext.getOrDefault("userMessage", ""));
            StructuredAiResults.AuroraResult criticCandidate = spoken;
            List<String> issuesAtCriticStart = List.copyOf(observableIssues);
            var critique = ai.call(userId, "AURORA_CRITIC_" + mode, criticInstruction(), criticContext,
                StructuredAiResults.AuroraCriticResult.class,
                () -> deterministicCritic(criticCandidate, issuesAtCriticStart, fallback), client);
            if (critique != null && Boolean.FALSE.equals(critique.pass) && critique.repaired != null
                    && critique.repaired.segments != null && !critique.repaired.segments.isEmpty()) {
                spoken = critique.repaired;
                repaired = true;
            }
            if (critique != null && critique.issues != null) observableIssues = critique.issues;
        }
        return new Generation(spoken, "dual-kernel.v1", safe(plan.relationshipMove), repaired,
            observableIssues == null ? List.of() : List.copyOf(observableIssues));
    }

    private String planInstruction() {
        return """
            你是 Aurora 的理解与规划核。只输出 JSON，不写最终回复，也不暴露逐步思维。
            提取用户当前意图、最需要被怎样回应、关系动作、回复约束、每个气泡的作用、
            可用记忆 ID 和不确定性。打断发生时以最新输入重规划，未说出的旧计划不得当作共同经历。
            needsCritic 仅在安全、边界、强推断、关系修复或记忆不确定时为 true。
            """;
    }

    private String speakerInstruction() {
        return """
            你是 Aurora 的表达与关系核。严格依据 responsePlan 生成最终结构化 AuroraResult JSON。
            segments 是 1-3 条自然中文消息，每条承担不同作用；先贴合此刻，再自然推进。
            不诊断、不制造依赖、不假装人类、不复述内部计划。只引用 responsePlan 允许的记忆 ID。
            用户打断时先接受新方向，不重复被取消的旧建议。
            """;
    }

    private String criticInstruction() {
        return """
            你是有界的 Aurora critic。只检查候选是否违背计划、安全边界、用户打断、记忆授权、
            非诊断和不重复要求。输出 pass、issues；若不通过，repaired 给出完整 AuroraResult。
            不添加新事实，不扩张记忆，不输出分析过程。
            """;
    }

    private StructuredAiResults.AuroraPlanResult fallbackPlan(Map<String, Object> context) {
        var plan = new StructuredAiResults.AuroraPlanResult();
        plan.userIntent = safe(context.get("userMessage"));
        plan.emotionalNeed = "先准确接住用户此刻明确表达的需要";
        plan.relationshipMove = context.containsKey("interruptionContext") ? "接受打断并按新方向重规划" : "稳稳回应并把选择权交还用户";
        plan.responseConstraints = List.of("不诊断", "不制造依赖", "不假装确定", "不重复近期表达");
        plan.bubblePurposes = List.of("接住当下", "提供一个贴近语境的推进或问题");
        plan.relevantMemoryIds = List.of();
        plan.uncertainty = "未由真实模型形成规划，使用保守可复现计划";
        plan.needsCritic = context.containsKey("interruptionContext");
        return plan;
    }

    private void normalizePlan(StructuredAiResults.AuroraPlanResult plan, Map<String, Object> context) {
        if (plan.userIntent == null || plan.userIntent.isBlank()) plan.userIntent = safe(context.get("userMessage"));
        if (plan.emotionalNeed == null || plan.emotionalNeed.isBlank()) plan.emotionalNeed = "回应用户明确表达的需要";
        if (plan.relationshipMove == null || plan.relationshipMove.isBlank()) plan.relationshipMove = "保持连续并交还选择权";
        if (plan.responseConstraints == null || plan.responseConstraints.isEmpty())
            plan.responseConstraints = List.of("不诊断", "不制造依赖", "不虚构记忆");
        if (plan.bubblePurposes == null || plan.bubblePurposes.isEmpty()) plan.bubblePurposes = List.of("回应当下");
        if (plan.relevantMemoryIds == null) plan.relevantMemoryIds = List.of();
    }

    private List<String> qualityIssues(StructuredAiResults.AuroraResult result,
                                       StructuredAiResults.AuroraPlanResult plan) {
        List<String> issues = new ArrayList<>();
        if (result == null || result.segments == null || result.segments.isEmpty()) issues.add("empty_response");
        if (result != null && result.segments != null && result.segments.size() > 3) issues.add("too_many_bubbles");
        if (result != null && result.segments != null && result.segments.stream().anyMatch(s -> s != null && s.length() > 300))
            issues.add("bubble_too_long");
        if (result != null && result.referencedMemoryIds != null && plan.relevantMemoryIds != null
                && !plan.relevantMemoryIds.containsAll(result.referencedMemoryIds)) issues.add("unauthorized_memory_expansion");
        return issues;
    }

    private StructuredAiResults.AuroraCriticResult deterministicCritic(StructuredAiResults.AuroraResult spoken,
                                                                         List<String> issues,
                                                                         Supplier<StructuredAiResults.AuroraResult> fallback) {
        var result = new StructuredAiResults.AuroraCriticResult();
        result.pass = issues == null || issues.isEmpty();
        result.issues = issues == null ? List.of() : List.copyOf(issues);
        result.repaired = result.pass ? spoken : fallback.get();
        return result;
    }

    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }

    public record Generation(StructuredAiResults.AuroraResult result, String runtime,
                             String relationshipMove, boolean repaired, List<String> criticIssues) {}
}
