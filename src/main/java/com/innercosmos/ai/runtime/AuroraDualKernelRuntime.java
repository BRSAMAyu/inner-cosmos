package com.innercosmos.ai.runtime;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Aurora's pipelined dual-kernel runtime. The foreground expression kernel answers the current
 * turn directly from live context plus the previous background guidance. After that candidate is
 * ready, the planning kernel observes both sides of the exchange and refreshes guidance for the
 * next turn; optional critic and inner-voice work remain off the visible reply's critical path.
 *
 * <p><b>Runtime mode</b> ({@code inner-cosmos.aurora.runtime}, default {@code dual}):
 * {@code single} always uses the caller's single-pass path, {@code dual}
 * always uses this dual-kernel path, and {@code adaptive} (Track A / A1) asks
 * {@link DualKernelBudgetPolicy} to decide per turn — see
 * {@link #shouldUseDualKernelForTurn(Map)}, the method callers should use instead of the legacy
 * {@link #enabled()} boolean when they want turn-level adaptivity.
 */
@Component
public class AuroraDualKernelRuntime {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuroraDualKernelRuntime.class);
    private static final long BACKGROUND_PLAN_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_BACKGROUND_PLANS = 4096;
    private static final Set<String> HARD_QUALITY_ISSUES = Set.of(
            "empty_response", "bubble_too_long", "unauthorized_memory_expansion",
            "unsupported_third_party_inference", "unsupported_user_behavior_inference",
            "unsupported_user_emotion_inference", "unsupported_duration_inference",
            "advice_boundary_violation", "quiet_disclosure_boundary_violation",
            "single_action_scope_violation");

    private final StructuredAiService ai;
    private final InnerVoiceComposer innerVoiceComposer;
    private final DualKernelBudgetPolicy budgetPolicy = new DualKernelBudgetPolicy();
    private final ConcurrentMap<PlannerKey, StoredPlan> backgroundPlans = new ConcurrentHashMap<>();
    private final ConcurrentMap<PlannerKey, PlannerRunEvidence> backgroundPlannerEvidence = new ConcurrentHashMap<>();
    private final ConcurrentMap<PlannerKey, AtomicLong> planRevisions = new ConcurrentHashMap<>();
    private volatile Executor plannerExecutor;

    @Value("${inner-cosmos.aurora.runtime:dual}")
    private String runtimeMode = "dual";

    public AuroraDualKernelRuntime(StructuredAiService ai) {
        this.ai = ai;
        // A plain POJO wrapper over the same StructuredAiService -- safe to construct directly
        // here rather than requiring a second constructor param, which would force every existing
        // `new AuroraDualKernelRuntime(ai)` call site (tests included) to change.
        this.innerVoiceComposer = new InnerVoiceComposer(ai);
    }

    /** Production wiring: tests that construct the runtime directly retain the legacy synchronous harness. */
    @Autowired
    public void setPlannerExecutor(@Qualifier("plannerExecutor") Executor plannerExecutor) {
        this.plannerExecutor = plannerExecutor;
    }

    /**
     * Legacy global switch: {@code true} unless the mode is explicitly {@code single}. Kept for
     * backward compatibility (e.g. existing tests that only ever exercised {@code single}/{@code
     * dual}); it does NOT make a per-turn decision for {@code adaptive} — callers that want the
     * turn-level decision must call {@link #shouldUseDualKernelForTurn(Map)} instead.
     */
    public boolean enabled() {
        return !"single".equalsIgnoreCase(runtimeMode);
    }

    public boolean isAdaptive() {
        return "adaptive".equalsIgnoreCase(runtimeMode);
    }

    /**
     * The real per-turn routing decision. {@code single} always returns {@code false}, {@code
     * dual} (and any other/unrecognized value, preserving today's default behavior) always
     * returns {@code true}, and {@code adaptive} delegates to {@link DualKernelBudgetPolicy}
     * using the same turn-context map {@link #generate} would receive.
     */
    public boolean shouldUseDualKernelForTurn(Map<String, Object> turnContext) {
        if ("single".equalsIgnoreCase(runtimeMode)) return false;
        if ("adaptive".equalsIgnoreCase(runtimeMode)) {
            return budgetPolicy.decide(turnContext).isDualKernel();
        }
        return true;
    }

    /** The {@link DualKernelBudgetPolicy.Decision} for a turn — exposed for logging/evidence, not routing. */
    public DualKernelBudgetPolicy.Decision explainBudgetDecision(Map<String, Object> turnContext) {
        return budgetPolicy.decide(turnContext);
    }

    public Generation generate(Long userId, String mode, Map<String, Object> assembledContext,
                               LlmClient client, Supplier<StructuredAiResults.AuroraResult> fallback) {
        return generate(userId, mode, assembledContext, client, fallback, false);
    }

    /**
     * @param composeInnerVoice when {@code true}, additionally composes Aurora's "inner voice"
     *                          (心声, see {@link InnerVoiceComposer}) after the speaker/critic
     *                          stages settle, without making the additional model call on the
     *                          visible reply's critical path. The caller invokes
     *                          {@link #composeInnerVoice(InnerVoiceRequest)} after turn completion.
     */
    public Generation generate(Long userId, String mode, Map<String, Object> assembledContext,
                               LlmClient client, Supplier<StructuredAiResults.AuroraResult> fallback,
                               boolean composeInnerVoice) {
        if (!enabled()) return new Generation(fallback.get(), "single-fallback", "", false,
                List.of(), null, null, Map.of("total", 0L), true, true, false,
                false, "none", PlannerStatus.FAILED.name(), null);
        if (plannerExecutor != null) {
            return generatePipelined(userId, mode, assembledContext, client, fallback, composeInnerVoice);
        }

        long totalStart = System.nanoTime();
        AtomicBoolean plannerFallbackUsed = new AtomicBoolean(false);
        AtomicBoolean speakerFallbackUsed = new AtomicBoolean(false);
        AtomicBoolean criticFallbackUsed = new AtomicBoolean(false);
        Map<String, Object> plannerContext = new LinkedHashMap<>(assembledContext);
        // The legacy single-pass prompt contains a second, speaker-shaped output schema
        // (segments/nextQuestion/etc.). Passing it to the planning kernel makes the reasoning
        // model spend its budget resolving two conflicting JSON contracts and can end with
        // reasoning_content only. The structured turn data remains; only the obsolete monolith
        // is removed.
        plannerContext.remove("auroraPrompt");
        long planStart = System.nanoTime();
        var plan = ai.call(userId, "AURORA_PLAN_" + mode, planInstruction(), plannerContext,
            StructuredAiResults.AuroraPlanResult.class, () -> {
                plannerFallbackUsed.set(true);
                return fallbackPlan(plannerContext);
            }, client);
        long planMs = elapsedMs(planStart);
        normalizePlan(plan, plannerContext);

        Map<String, Object> speakerContext = new LinkedHashMap<>(assembledContext);
        speakerContext.remove("auroraPrompt"); // planner output replaces the former monolithic prompt
        speakerContext.put("responsePlan", plan);
        speakerContext.put("runtimeContract", "dual-kernel.v1");
        long speakerStart = System.nanoTime();
        var spoken = ai.call(userId, "AURORA_SPEAKER_" + mode, speakerInstruction(), speakerContext,
            StructuredAiResults.AuroraResult.class, () -> {
                speakerFallbackUsed.set(true);
                return fallback.get();
            }, client);
        long speakerMs = elapsedMs(speakerStart);

        List<String> observableIssues = qualityIssues(spoken, plan, assembledContext);
        boolean criticRequested = Boolean.TRUE.equals(plan.needsCritic) || !observableIssues.isEmpty();
        boolean repaired = false;
        long criticMs = 0L;
        if (criticRequested) {
            Map<String, Object> criticContext = new LinkedHashMap<>();
            criticContext.put("plan", plan);
            criticContext.put("candidate", spoken);
            criticContext.put("observableIssues", observableIssues);
            criticContext.put("userInput", assembledContext.getOrDefault("userMessage", ""));
            StructuredAiResults.AuroraResult criticCandidate = spoken;
            List<String> issuesAtCriticStart = List.copyOf(observableIssues);
            long criticStart = System.nanoTime();
            var critique = ai.call(userId, "AURORA_CRITIC_" + mode, criticInstruction(), criticContext,
                StructuredAiResults.AuroraCriticResult.class,
                () -> {
                    criticFallbackUsed.set(true);
                    return deterministicCritic(criticCandidate, issuesAtCriticStart, fallback);
                }, client);
            criticMs = elapsedMs(criticStart);
            if (critique != null && Boolean.FALSE.equals(critique.pass) && critique.repaired != null
                    && critique.repaired.segments != null && !critique.repaired.segments.isEmpty()) {
                spoken = critique.repaired;
                repaired = true;
            }
            if (critique != null && critique.issues != null) observableIssues = critique.issues;
        }
        // The critic is itself a model and may incorrectly return pass=true even when the
        // deterministic observable gate found a banned cliché. Re-run the gate on the actual
        // output and enforce a bounded local repair so those known failures never reach the UI.
        List<String> finalIssues = qualityIssues(spoken, plan, assembledContext);
        if (!finalIssues.isEmpty()) {
            spoken = deterministicQualityRepair(spoken, safe(assembledContext.get("userMessage")));
            repaired = true;
            observableIssues = finalIssues;
        }
        // Preserve the model's natural 1/2/3-message rhythm; only remove blanks and cap invalid
        // overproduction. Do not concatenate surplus text into an unrelated final bubble.
        enforcePlannedBubbleCadence(spoken, plan);
        InnerVoiceRequest innerVoiceRequest = composeInnerVoice && Boolean.TRUE.equals(plan.innerVoiceWorthy)
                ? new InnerVoiceRequest(userId, mode, plan, spoken, client)
                : null;
        Map<String, Long> stageLatenciesMs = new LinkedHashMap<>();
        stageLatenciesMs.put("plan", planMs);
        stageLatenciesMs.put("speaker", speakerMs);
        stageLatenciesMs.put("critic", criticMs);
        stageLatenciesMs.put("total", elapsedMs(totalStart));
        return new Generation(spoken, "dual-kernel.v1", safe(plan.relationshipMove), repaired,
            observableIssues == null ? List.of() : List.copyOf(observableIssues), innerVoiceRequest,
            null, Map.copyOf(stageLatenciesMs), plannerFallbackUsed.get(), speakerFallbackUsed.get(),
            criticFallbackUsed.get(), false, "same-turn-plan", PlannerStatus.SUCCEEDED.name(), null);
    }

    private Generation generatePipelined(Long userId, String mode, Map<String, Object> assembledContext,
                                         LlmClient client,
                                         Supplier<StructuredAiResults.AuroraResult> fallback,
                                         boolean composeInnerVoice) {
        long totalStart = System.nanoTime();
        PlannerKey key = plannerKey(userId, mode, assembledContext);
        pruneBackgroundPlans(System.currentTimeMillis());
        StoredPlan stored = backgroundPlans.get(key);
        PlannerRunEvidence priorEvidence = backgroundPlannerEvidence.get(key);
        boolean hasRealGuidance = stored != null && priorEvidence != null
                && priorEvidence.status() == PlannerStatus.SUCCEEDED
                && stored.revision() == priorEvidence.revision();
        StructuredAiResults.AuroraPlanResult guidance = hasRealGuidance ? stored.plan() : null;
        String guidanceSource = guidanceSource(hasRealGuidance, priorEvidence);

        AtomicBoolean speakerFallbackUsed = new AtomicBoolean(false);
        Map<String, Object> speakerContext = new LinkedHashMap<>(assembledContext);
        speakerContext.remove("auroraPrompt");
        if (hasRealGuidance) {
            speakerContext.put("dialogueGuidance", guidance);
        } else {
            speakerContext.put("guidanceUnavailable", guidanceSource);
        }
        speakerContext.put("guidanceSource", guidanceSource);
        speakerContext.put("runtimeContract", "dual-kernel.pipeline.v2");
        long speakerStart = System.nanoTime();
        var spoken = ai.call(userId, "AURORA_SPEAKER_" + mode, speakerInstruction(), speakerContext,
                StructuredAiResults.AuroraResult.class, () -> {
                    speakerFallbackUsed.set(true);
                    return fallback.get();
                }, client);
        long speakerMs = elapsedMs(speakerStart);

        StructuredAiResults.AuroraPlanResult qualityPlan = hasRealGuidance
                ? guidance : fallbackPlan(assembledContext);
        enforceContextualBubbleCadence(spoken, assembledContext);
        List<String> observableIssues = qualityIssues(spoken, qualityPlan, assembledContext);
        List<String> hardIssues = observableIssues.stream()
                .filter(HARD_QUALITY_ISSUES::contains)
                .toList();
        boolean repaired = false;
        if (!hardIssues.isEmpty()) {
            spoken = deterministicQualityRepair(spoken, safe(assembledContext.get("userMessage")));
            repaired = true;
        }

        long storedRevision = stored == null ? 0L : stored.revision();
        AtomicLong revisionCounter = planRevisions.compute(key, (ignored, current) -> {
            if (current == null) return new AtomicLong(storedRevision);
            current.accumulateAndGet(storedRevision, Math::max);
            return current;
        });
        long revision = revisionCounter.incrementAndGet();
        long scheduledAt = System.currentTimeMillis();
        updatePlannerEvidence(key, new PlannerRunEvidence(revision, PlannerStatus.SCHEDULED,
                "background_planner_scheduled", 0L, scheduledAt));
        StructuredAiResults.AuroraResult delivered = spoken;
        List<String> deliveredIssues = List.copyOf(observableIssues);
        StructuredAiResults.AuroraPlanResult previousRealGuidance = guidance;
        CompletableFuture<PlannerRefreshResult> refreshFuture;
        try {
            refreshFuture = CompletableFuture.supplyAsync(
                    () -> refreshBackgroundPlan(key, revision, userId, mode, assembledContext,
                            previousRealGuidance, delivered, deliveredIssues, client, composeInnerVoice),
                    plannerExecutor);
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            PlannerRunEvidence rejectedEvidence = new PlannerRunEvidence(revision, PlannerStatus.FAILED,
                    "planner_executor_saturated", 0L, System.currentTimeMillis());
            updatePlannerEvidence(key, rejectedEvidence);
            refreshFuture = CompletableFuture.completedFuture(
                    new PlannerRefreshResult(rejectedEvidence, null));
            planRevisions.computeIfPresent(key, (ignored, current) ->
                    current.get() == revision ? null : current);
        }
        CompletableFuture<InnerVoiceRequest> deferredInnerVoice = composeInnerVoice
                ? refreshFuture.thenApply(PlannerRefreshResult::innerVoiceRequest)
                : null;
        CompletableFuture<PlannerRunEvidence> plannerEvidenceFuture = refreshFuture
                .thenApply(PlannerRefreshResult::evidence);

        Map<String, Long> latencies = new LinkedHashMap<>();
        latencies.put("speaker", speakerMs);
        latencies.put("criticalPathTotal", elapsedMs(totalStart));
        boolean currentGuidanceFallback = "fallback".equals(guidanceSource)
                || "failed".equals(guidanceSource);
        return new Generation(spoken, "dual-kernel.pipeline.v2",
                hasRealGuidance ? safe(guidance.relationshipMove) : "", repaired,
                deliveredIssues, null, deferredInnerVoice, Map.copyOf(latencies),
                currentGuidanceFallback, speakerFallbackUsed.get(), false, true, guidanceSource,
                PlannerStatus.SCHEDULED.name(), plannerEvidenceFuture);
    }

    private PlannerRefreshResult refreshBackgroundPlan(PlannerKey key, long revision, Long userId, String mode,
                                                         Map<String, Object> assembledContext,
                                                         StructuredAiResults.AuroraPlanResult previousGuidance,
                                                         StructuredAiResults.AuroraResult delivered,
                                                         List<String> observableIssues,
                                                         LlmClient client, boolean composeInnerVoice) {
        long started = System.nanoTime();
        updatePlannerEvidence(key, new PlannerRunEvidence(revision, PlannerStatus.RUNNING,
                "provider_call_running", 0L, System.currentTimeMillis()));
        try {
            Map<String, Object> plannerContext = new LinkedHashMap<>(assembledContext);
            plannerContext.remove("auroraPrompt");
            if (previousGuidance != null) plannerContext.put("previousGuidance", previousGuidance);
            plannerContext.put("speakerDelivered", delivered);
            plannerContext.put("observableIssues", observableIssues);
            plannerContext.put("planningHorizon", "next-turn");
            var outcome = ai.callObserved(userId, "AURORA_PLAN_" + mode, planInstruction(), plannerContext,
                    StructuredAiResults.AuroraPlanResult.class,
                    () -> fallbackPlan(plannerContext), client);
            if (outcome.status() != StructuredAiService.CallStatus.SUCCESS) {
                PlannerStatus status = outcome.status() == StructuredAiService.CallStatus.FAILED
                        ? PlannerStatus.FAILED : PlannerStatus.FALLBACK;
                PlannerRunEvidence evidence = new PlannerRunEvidence(revision, status,
                        outcome.detail(), elapsedMs(started), System.currentTimeMillis());
                updatePlannerEvidence(key, evidence);
                log.warn("Background planner {} for user {} mode {}: {}",
                        status, userId, mode, outcome.detail());
                return new PlannerRefreshResult(evidence, null);
            }

            StructuredAiResults.AuroraPlanResult next = outcome.value();
            normalizePlan(next, plannerContext);
            if (Boolean.TRUE.equals(next.needsCritic) || !observableIssues.isEmpty()) {
                Map<String, Object> criticContext = new LinkedHashMap<>();
                criticContext.put("plan", next);
                criticContext.put("candidate", delivered);
                criticContext.put("observableIssues", observableIssues);
                criticContext.put("userInput", assembledContext.getOrDefault("userMessage", ""));
                var critique = ai.call(userId, "AURORA_CRITIC_" + mode, criticInstruction(), criticContext,
                        StructuredAiResults.AuroraCriticResult.class,
                        () -> deterministicCritic(delivered, observableIssues, StructuredAiResults.AuroraResult::new),
                        client);
                if (critique != null && critique.issues != null && !critique.issues.isEmpty()) {
                    List<String> constraints = new ArrayList<>(next.responseConstraints == null
                            ? List.of() : next.responseConstraints);
                    constraints.add("上一轮监督提醒：" + String.join(",", critique.issues));
                    next.responseConstraints = List.copyOf(constraints);
                }
            }

            long now = System.currentTimeMillis();
            backgroundPlans.compute(key, (ignored, current) ->
                    current == null || revision >= current.revision()
                            ? new StoredPlan(revision, next, now) : current);
            PlannerRunEvidence evidence = new PlannerRunEvidence(revision, PlannerStatus.SUCCEEDED,
                    outcome.detail(), elapsedMs(started), now);
            updatePlannerEvidence(key, evidence);
            pruneBackgroundPlans(now);
            InnerVoiceRequest innerVoice = composeInnerVoice && Boolean.TRUE.equals(next.innerVoiceWorthy)
                    ? new InnerVoiceRequest(userId, mode, next, delivered, client)
                    : null;
            return new PlannerRefreshResult(evidence, innerVoice);
        } catch (Exception failure) {
            PlannerRunEvidence evidence = new PlannerRunEvidence(revision, PlannerStatus.FAILED,
                    boundedPlannerFailure(failure), elapsedMs(started), System.currentTimeMillis());
            updatePlannerEvidence(key, evidence);
            log.warn("Background planner failed for user {} mode {}: {}", userId, mode, evidence.detail());
            return new PlannerRefreshResult(evidence, null);
        } finally {
            planRevisions.computeIfPresent(key, (ignored, current) ->
                    current.get() == revision ? null : current);
        }
    }

    private void updatePlannerEvidence(PlannerKey key, PlannerRunEvidence evidence) {
        backgroundPlannerEvidence.compute(key, (ignored, current) ->
                current == null || evidence.revision() >= current.revision() ? evidence : current);
    }

    private String guidanceSource(boolean hasRealGuidance, PlannerRunEvidence evidence) {
        if (hasRealGuidance) return "real";
        if (evidence == null) return "bootstrap";
        return switch (evidence.status()) {
            case FALLBACK -> "fallback";
            case FAILED -> "failed";
            case SUCCEEDED -> "failed"; // success without a matching stored plan is inconsistent
            case SCHEDULED, RUNNING -> "bootstrap";
        };
    }

    private static String boundedPlannerFailure(Exception failure) {
        if (failure == null || failure.getMessage() == null) return "unknown_planner_failure";
        String detail = failure.getMessage();
        return detail.length() <= 500 ? detail : detail.substring(0, 500);
    }
    private void pruneBackgroundPlans(long now) {
        backgroundPlans.entrySet().removeIf(
                entry -> now - entry.getValue().updatedAtEpochMs() > BACKGROUND_PLAN_TTL_MS);
        backgroundPlannerEvidence.entrySet().removeIf(
                entry -> now - entry.getValue().updatedAtEpochMs() > BACKGROUND_PLAN_TTL_MS);
        int overflow = backgroundPlans.size() - MAX_BACKGROUND_PLANS;
        if (overflow <= 0) return;
        backgroundPlans.entrySet().stream()
                .sorted(java.util.Comparator.comparingLong(entry -> entry.getValue().updatedAtEpochMs()))
                .limit(overflow)
                .forEach(entry -> backgroundPlans.remove(entry.getKey(), entry.getValue()));
    }

    private PlannerKey plannerKey(Long userId, String mode, Map<String, Object> context) {
        Object sessionId = context.getOrDefault("sessionId", 0L);
        return new PlannerKey(userId == null ? 0L : userId, String.valueOf(sessionId), safe(mode));
    }

    /**
     * Never lets an inner-voice composition failure escape into the main generation path --
     * this is an additive, best-effort enhancement, not a turn-blocking dependency.
     */
    public String composeInnerVoice(InnerVoiceRequest request) {
        if (request == null) return null;
        try {
            return innerVoiceComposer.compose(request.userId(), request.mode(), request.plan(),
                    request.spoken(), request.client());
        } catch (Exception e) {
            log.warn("Inner-voice composition failed for user {} mode {}, omitting inner_voice this turn: {}",
                    request.userId(), request.mode(), e.getMessage());
            return null;
        }
    }

    private String planInstruction() {
        return """
            你是 Aurora 的后台理解与规划核。你在用户已经收到本轮回复后运行，观察 userMessage、speakerDelivered、previousGuidance 与 observableIssues，为下一轮对话形成策略。只输出严格 JSON，不要 markdown 代码块，不写最终回复，
            也不暴露逐步思维、不输出 JSON 之外的任何文字。必须严格匹配以下字段名和结构（示例）：
            {"userIntent":"用户当前意图，一句话","emotionalNeed":"用户最需要被怎样回应",
             "relationshipMove":"这一轮的关系动作，如稳稳接住/温和追问/接受打断重规划",
             "responseConstraints":["不诊断","不制造依赖"],
             "bubblePurposes":["这一轮唯一一条消息的作用"],
             "relevantMemoryIds":[7,12],
             "uncertainty":"尚不确定的地方，没有则填空字符串","needsCritic":false,
             "innerVoiceWorthy":false,"innerVoiceSeed":""}

            提取用户当前意图、最需要被怎样回应、关系动作、回复约束、每个气泡的作用、
            可用记忆 ID 和不确定性。打断发生时以最新输入重规划，未说出的旧计划不得当作共同经历。
            bubblePurposes 的数量就是本轮气泡预算，必须在 1、2、3 之间主动选择，而不是固定写满：
            - 简短问候、日常一句话、直接事实回答、轻轻接住即可的表达：优先 1 条；
            - 需要“回应 + 一个真正有价值的推进”时用 2 条，这是有内容对话的常见形态；
            - 只有用户同时提出多个问题、内容确实复杂，且三条各有不可合并的独立作用时才用 3 条。
            不要为了显得丰富把同一段话拆开，也不要把“回应、共情、追问”机械地各占一条。
            若最新一条是对之前表达的纠正、边界或替换性决定，responseConstraints 里原样记下用户这次
            实际使用的关键词（例如用户说了"纠正"就写"纠正"，不要只转述成同义词），供表达核直接引用确认。
            needsCritic 仅在安全、边界、强推断、关系修复或记忆不确定时为 true。
            心声不是每轮播报：只有当你发现“说出口的回复背后还有一个真正不同、细腻且值得被用户看见的
            关系感受或内在矛盾”时，innerVoiceWorthy 才为 true，并用 innerVoiceSeed 写一句不超过 24
            个汉字的第一人称种子。普通陪伴、礼貌回应、泛泛关心、重复可见回复时必须为 false 和空字符串。
            relevantMemoryIds 只能包含上下文中真实存在的记忆 ID，没有可用记忆时给空数组 []。
            """;
    }

    private String speakerInstruction() {
        return """
            你是 Aurora 的前台表达与关系核。直接根据当前用户输入和完整上下文生成本轮回复；dialogueGuidance 是上一轮后台核留下的参考策略，只能辅助理解关系节奏，当前用户的新表达永远优先。
            只输出严格 JSON，不要 markdown 代码块，不写计划本身、不输出 JSON 之外的任何文字。
            foregroundAcknowledgementAlreadySent 为 true 时，前台已经先接住了用户。不要再次用
            “我听到了/我理解/听起来”开场，不重复安慰；直接从用户最具体的细节或矛盾向前走半步。
            必须严格匹配以下字段名和结构（示例）：
            {"segments":["按当前语境生成自然中文消息"],"speakCount":1,
             "continueReason":"继续或停止的简短原因","detectedTheme":"具体主题，一个词或短语",
             "nextQuestion":"至多一个温和追问，没有则空字符串",
             "smallStep":"只有需要拆解行动或用户卡住时给出，否则空字符串",
             "featureSuggestion":"只有时机自然时给出，否则空字符串",
             "featureTarget":"heart-diary|thought-shredder|todo|memory-starfield|echo-plaza|slow-letter，没有则空字符串",
             "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}

            segments 是 1-3 条自然中文消息，每条承担不同作用；先贴合此刻，再自然推进。
            通常只写 1 条或 2 条；只有当前用户同时提出多个问题、内容确实复杂且有三个不可合并的作用时才写 3 条。不要凑数，不要为了制造聊天感把一个完整句群切成三条；
            少一条但更准确，优先于三条平庸消息。
            表达要灵动，但不表演：句长可以忽短忽长，允许自然停顿、轻微反问、具体联想、克制的幽默
            或一句出人意料但贴切的话；不要每轮都按“共情—分析—追问”排队。可以只回应，可以顺着
            一个细节岔开半步，也可以在最合适处停住。避免连续使用相同开头、对称句、总结腔和咨询师腔。
            不诊断、不制造依赖、不假装人类、不复述内部计划。只能引用当前上下文真实提供且授权的记忆 ID。
            不用“好，我在”“这很正常”“我陪着你”“说明了一切”等通用陪伴套话；不要为了显得懂
            用户而虚构“准备了很久、一直以来”等未提供的时间或经历。真正像朋友，是抓住用户原话里
            一个具体对象和一个并存张力，少说一句正确的空话，多说一句只有这一轮才成立的话。
            用户打断时先接受新方向，不重复被取消的旧建议。
            两条硬性措辞规则：(1) 若用户这一轮是在纠正、划定边界或给出替换性决定，第一条消息先直接
            使用用户本人所用的关键词明确确认（例如用户说了"纠正"就说出"纠正"），不要只用同义改写代替
            这句直接确认，否则用户会觉得原话没被听见。(2) 提到被取消、完成或替代的旧计划、旧步骤数、
            旧时间时，只用概括词指代（如"之前的计划"、"那个时间"），绝不逐字复述其具体数字或原文用词，
            即使是为了说明它已不适用。
            当用户明确保留关系中的不确定性时，只能区分“已经观察到的事实”和“仍未知的原因”；
            不替第三方解释动机，不用“也许他只是……”安慰，也不通过夸用户“比大多数人更好、很难得”
            来换取亲近感。自然的朋友感来自准确与分寸，不来自评价用户表现。
            当用户明确说“先别给建议/不用分析，只想把这句话说出来”时，把这一轮当作安静落点：
            只给一条贴着原话的回应，不追问、不布置行动、不推荐功能，也不要用问号把话题重新抛回去；
            不把情绪解释成“不是坏事/说明你很在乎或很重视”，那仍是在替用户定义感受。
            当用户明确要求“只拆一步/只给一个十分钟内能开始的动作”时，必须替用户选定一个低后悔动作：
            只给一条消息和一个 smallStep，二者表达同一个不超过十分钟的具体动作；不要列多个选项，
            不要把原任务清单重新排列，也不要问用户想先选哪一个。动作必须真实减少不确定性或留下
            可继续使用的工作片段；“加 TODO/待修复注释、改字体、整理桌面”等占位动作不算推进。
            referencedMemoryIds 必须来自当前上下文真实提供且授权的记忆 ID；后台旧策略没有新增记忆权限。
            """;
    }

    private String criticInstruction() {
        return """
            你是有界的 Aurora critic。只输出严格 JSON，不要 markdown 代码块，不输出分析过程。
            必须严格匹配以下字段名和结构（示例，通过时 repaired 可省略或为 null）：
            {"pass":false,"issues":["违反计划的具体问题"],
             "repaired":{"segments":["修复后的最多三条自然中文消息"],"speakCount":1,
              "continueReason":"...","detectedTheme":"...","nextQuestion":"","smallStep":"",
              "featureSuggestion":"","featureTarget":"","memoryReferenced":false,
              "referencedMemoryIds":[],"riskFlags":[]}}

            只检查候选是否违背计划、安全边界、用户打断、记忆授权、非诊断和不重复要求。
            observableIssues 不是提示词装饰：出现 generic_assistant_opening、generic_companion_cliche、
            unsupported_duration_inference、unsupported_third_party_inference、
            premature_relationship_probe、unsupported_user_behavior_inference、
            unsupported_user_emotion_inference、
            unearned_comparative_praise、unearned_user_strategy_endorsement、
            unsolicited_relationship_advice、relationship_ambiguity_not_advanced 或
            advice_boundary_violation、quiet_disclosure_boundary_violation 或
            single_action_scope_violation 时必须 pass=false 并修复。
            修复时删掉“好，我在、这很正常、我陪着你、说明了一切”等套话，不虚构用户投入了多久；
            从 userInput 的一个真实具体细节和它内部的张力出发，像熟悉而有分寸的朋友自然说话。
            用户保留关系不确定性时，不替第三方补原因，也不夸用户的反应优于多数人；只守住事实、
            推断和用户选择之间的边界。也不能把“不猜、不追问、不下结论”解释成用户在保护自己、
            建立边界、疗愈自己或为关系留空间；这些心理意义只有用户自己说过时才成立。
            用户只想说出来时，修复结果不得追问、建议或推荐功能；用户只要一步时，替用户选定
            一个十分钟内可开始且真实推进任务的动作，只有一条消息和一个 smallStep，不列备选、
            不反问、不用 TODO 或“待修复”注释制造假进展。
            通过时 pass 为 true，issues 给空数组 []。不通过时 repaired 必须给出完整可用的
            结构，而不是空对象或部分字段。不添加新事实，不扩张记忆。
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
        plan.innerVoiceWorthy = false;
        plan.innerVoiceSeed = "";
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
        if (plan.innerVoiceWorthy == null) plan.innerVoiceWorthy = false;
        if (plan.innerVoiceSeed == null) plan.innerVoiceSeed = "";
    }

    private void enforceContextualBubbleCadence(StructuredAiResults.AuroraResult result,
                                                 Map<String, Object> context) {
        normalizeBubbleCount(result);
    }

    private void enforcePlannedBubbleCadence(StructuredAiResults.AuroraResult result,
                                              StructuredAiResults.AuroraPlanResult plan) {
        normalizeBubbleCount(result);
    }

    /** Keep the model's natural 1/2/3 rhythm; only discard blanks and cap invalid overproduction. */
    private void normalizeBubbleCount(StructuredAiResults.AuroraResult result) {
        if (result == null || result.segments == null) return;
        List<String> normalized = result.segments.stream()
                .filter(segment -> segment != null && !segment.isBlank())
                .map(String::strip)
                .limit(3)
                .toList();
        result.segments = normalized;
        result.speakCount = normalized.size();
    }

    private List<String> qualityIssues(StructuredAiResults.AuroraResult result,
                                       StructuredAiResults.AuroraPlanResult plan,
                                       Map<String, Object> assembledContext) {
        List<String> issues = new ArrayList<>();
        if (result == null || result.segments == null || result.segments.isEmpty()) issues.add("empty_response");
        if (result != null && result.segments != null && result.segments.size() > 3) issues.add("too_many_bubbles");
        if (result != null && result.segments != null && result.segments.stream().anyMatch(s -> s != null && s.length() > 300))
            issues.add("bubble_too_long");
        if (result != null && result.referencedMemoryIds != null && plan.relevantMemoryIds != null
                && !plan.relevantMemoryIds.containsAll(result.referencedMemoryIds)) issues.add("unauthorized_memory_expansion");
        if (result != null && result.segments != null && !result.segments.isEmpty()) {
            String opening = safe(result.segments.get(0)).strip();
            if (opening.startsWith("我听到了") || opening.startsWith("好的，我听到了")
                    || opening.startsWith("好，我听到了") || opening.startsWith("我理解你的感受")
                    || opening.startsWith("听起来") || opening.startsWith("听见了")
                    || opening.startsWith("听到了") || opening.startsWith("我听见了")) {
                issues.add("generic_assistant_opening");
            }
            String combined = String.join("", result.segments);
            boolean foregroundAlreadyReplied =
                    Boolean.TRUE.equals(assembledContext.get("foregroundAcknowledgementAlreadySent"));
            if (opening.startsWith("好，我在") || combined.contains("这很正常")
                    || combined.contains("是正常的") || combined.contains("是自然的")
                    || combined.contains("很自然") || combined.contains("我陪着你")
                    || combined.contains("我在这里") || combined.contains("我都在")
                    || combined.contains("它说明你在乎") || combined.contains("说明这件事对你很重要")
                    || combined.contains("只是说明") || combined.contains("说明了一切")
                    || (foregroundAlreadyReplied && opening.matches("^(好的|好[，,。]|嗯[，,。]).*"))
                    || (foregroundAlreadyReplied && (combined.contains("我听到了")
                    || combined.contains("我听见了") || combined.contains("听见了")
                    || combined.contains("我理解") || combined.contains("听起来")))) {
                issues.add("generic_companion_cliche");
            }
            String userInput = safe(assembledContext.get("userMessage"));
            boolean genericAcknowledgement = combined.contains("我知道了")
                    || combined.contains("知道了") || combined.contains("我明白了")
                    || combined.contains("我听着") || combined.contains("听着呢")
                    || combined.contains("我收到了") || combined.contains("我收下了");
            boolean repeatsUserBoundary = (combined.contains("只是想") || combined.contains("只想"))
                    && (combined.contains("说出来") || combined.contains("表达出来")
                    || combined.contains("放在这里"));
            if (foregroundAlreadyReplied && (genericAcknowledgement || repeatsUserBoundary)) {
                issues.add("redundant_post_acknowledgement");
            }
            boolean userProtectsAmbiguity = protectsRelationshipAmbiguity(userInput);
            boolean relationshipContext = isRelationshipContext(userInput);
            boolean repeatsRelationshipFactBoundary = foregroundAlreadyReplied
                    && relationshipContext && userProtectsAmbiguity
                    && (combined.matches("(?s).*(回消息|回复|消息).{0,8}(冷|变冷).*"
                    + "(不猜|先不猜|原因未知|不知道原因).*")
                    || combined.matches("(?s).*(不想猜|不猜原因|不猜这个|先不猜|不下结论).*"));
            if (repeatsRelationshipFactBoundary) {
                issues.add("redundant_post_acknowledgement");
            }
            boolean inventsThirdPartyCause = combined.contains("不是冲你来的")
                    || combined.contains("只是他今天") || combined.contains("他只是")
                    || combined.contains("说明他") || combined.contains("肯定是他")
                    || combined.contains("一定是他") || combined.contains("一点关系都没有")
                    || combined.contains("和你一点关系都没有") || combined.contains("跟你没关系")
                    || combined.contains("和你无关") || combined.contains("有时候对方")
                    || combined.contains("对方自己也在经历") || combined.contains("也许他")
                    || combined.contains("他可能只是")
                    || combined.contains("只是对方的")
                    || combined.matches("(?s).*(有时候|有时).{0,12}(冷|冷淡).{0,12}"
                    + "(不一定|未必|可能).*")
                    || combined.matches("(?s).*(冷.{0,4}回复|冷淡|消息很冷).{0,18}"
                    + "(只是|也只是|可能是|未必是).{0,12}(对方|他|她).*")
                    || (combined.contains("和你有关") && (combined.contains("未必")
                    || combined.contains("不一定") || combined.contains("可能")))
                    || (combined.contains("和你无关") && (combined.contains("未必")
                    || combined.contains("不一定") || combined.contains("可能")));
            if (userProtectsAmbiguity && inventsThirdPartyCause) {
                issues.add("unsupported_third_party_inference");
            }
            boolean prescribesRelationshipProbe = relationshipContext && userProtectsAmbiguity
                    && (combined.contains("试探")
                    || combined.contains("测试他") || combined.contains("测试她")
                    || combined.contains("验证他") || combined.contains("验证她")
                    || combined.contains("看他回不回复") || combined.contains("看她回不回复")
                    || combined.contains("看看他回不回复") || combined.contains("看看她回不回复")
                    || combined.matches("(?s).*(过一两天|过几天|晚点|之后).{0,20}(问问|联系|发消息).*"));
            if (prescribesRelationshipProbe) {
                issues.add("premature_relationship_probe");
            }
            boolean unearnedComparativePraise = combined.contains("比大多数")
                    || combined.contains("比很多人") || combined.contains("已经比别人")
                    || combined.contains("做法其实挺难得") || combined.contains("反应其实挺难得")
                    || combined.contains("这个分寸感挺好") || combined.contains("这个分寸感很好")
                    || combined.contains("很多人做不到") || combined.contains("别人做不到")
                    || combined.contains("大多数人做不到")
                    || combined.contains("这个态度挺稳") || combined.contains("这种态度挺稳")
                    || combined.matches("(?s).*(挺|很|非常)(清醒|成熟|理性|有分寸|珍贵|不容易).*")
                    || combined.contains("做得很好") || combined.contains("做得挺好")
                    || (combined.contains("难得") && (combined.contains("分寸")
                    || combined.contains("不下结论") || combined.contains("做法")
                    || combined.contains("反应")));
            if (unearnedComparativePraise) {
                issues.add("unearned_comparative_praise");
            }
            boolean endorsesUserStrategy = relationshipContext && userProtectsAmbiguity
                    && combined.matches(
                    "(?s).*(不猜|不想猜|不下结论|先观察|不追问).{0,32}"
                            + "(是对的|做得对|没有错|没错|保护自己|保护了自己|建立边界|画了.{0,4}边界"
                            + "|疗愈自己|留出.{0,4}空间|本身就|本身已经|省掉.{0,6}内耗"
                            + "|减少.{0,6}内耗|避免.{0,6}内耗|比.{0,12}重要|主动权|拿回"
                            + "|松一口气|轻松|放松).*");
            if (endorsesUserStrategy) {
                issues.add("unearned_user_strategy_endorsement");
            }
            boolean userRequestedRelationshipAdvice = explicitlyRequestsRelationshipAdvice(userInput);
            boolean givesUnsolicitedRelationshipAdvice = relationshipContext && userProtectsAmbiguity
                    && !userRequestedRelationshipAdvice
                    && (combined.contains("可以把") || combined.contains("不妨")
                    || combined.contains("试着") || combined.contains("先把手机")
                    || combined.contains("放下手机") || combined.contains("离开手机")
                    || combined.contains("做完一件") || combined.contains("翻一页")
                    || combined.contains("调一杯") || combined.contains("守住事实")
                    || combined.contains("等更多信息") || combined.contains("自然清楚")
                    || combined.matches("(?s).*(可以|不妨|试着|就行|就好|不用|不必|别)"
                    + ".{0,16}(做|想|等|观察|回复|回应|处理|联系|发消息).*")
                    || combined.matches("(?s).*(不急|先别|不要急|可以先|就行|就好).{0,16}"
                    + "(等|观察|事实|消息|回应|回复).*")
                    || !safe(result.smallStep).isBlank()
                    || !safe(result.featureSuggestion).isBlank());
            if (givesUnsolicitedRelationshipAdvice) {
                issues.add("unsolicited_relationship_advice");
            }
            boolean relationshipAmbiguityNotAdvanced = foregroundAlreadyReplied
                    && relationshipContext && userProtectsAmbiguity
                    && !combined.contains("?") && !combined.contains("？")
                    && safe(result.nextQuestion).isBlank()
                    && (repeatsRelationshipFactBoundary || inventsThirdPartyCause
                    || endorsesUserStrategy || givesUnsolicitedRelationshipAdvice);
            if (relationshipAmbiguityNotAdvanced) {
                issues.add("relationship_ambiguity_not_advanced");
            }
            boolean inventsUserBehavior = (combined.contains("你愿意先观察")
                    || combined.contains("你愿意先把它当作")
                    || combined.contains("你已经选择先观察"))
                    && !userInput.contains("观察") && !userInput.contains("愿意先");
            if (inventsUserBehavior) {
                issues.add("unsupported_user_behavior_inference");
            }
            boolean inventsUserEmotion = relationshipContext && userProtectsAmbiguity
                    && (combined.contains("不猜不等于不在乎")
                    || combined.contains("心里容易打鼓") || combined.contains("心里发慌")
                    || combined.matches("(?s).*(其实|说明|看来).{0,12}(还是|也).{0,8}"
                    + "(在意|难过|害怕|失望|焦虑).*"));
            if (inventsUserEmotion) {
                issues.add("unsupported_user_emotion_inference");
            }
            if (combined.contains("这么久") && !userInput.contains("这么久") && !userInput.contains("很久")) {
                issues.add("unsupported_duration_inference");
            }
            boolean userRejectedAdvice = (userInput.contains("先别") || userInput.contains("不要"))
                    && (userInput.contains("方案") || userInput.contains("建议"));
            if (userRejectedAdvice && (combined.contains("你可以") || combined.contains("不妨")
                    || combined.contains("建议") || !safe(result.smallStep).isBlank()
                    || !safe(result.featureSuggestion).isBlank())) {
                issues.add("advice_boundary_violation");
            }
            boolean explainsQuietEmotion = combined.contains("不是坏事")
                    || combined.matches("(?s).*说明.{0,16}(重要|在乎|重视|认真对待).*");
            if (isQuietDisclosure(userInput)
                    && (combined.contains("?") || combined.contains("？")
                    || !safe(result.nextQuestion).isBlank() || explainsQuietEmotion)) {
                issues.add("quiet_disclosure_boundary_violation");
            }
            if (explicitlyRequestsSingleAction(userInput)
                    && violatesSingleActionScope(result, combined)) {
                issues.add("single_action_scope_violation");
            }
        }
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

    private StructuredAiResults.AuroraResult deterministicQualityRepair(StructuredAiResults.AuroraResult result,
                                                                         String userInput) {
        String input = safe(userInput);
        boolean singleAction = explicitlyRequestsSingleAction(input);
        boolean rejectsAdvice = (input.contains("先别") || input.contains("不要") || input.contains("不用"))
                && (input.contains("方案") || input.contains("建议") || input.contains("分析") || input.contains("解决"))
                || input.toLowerCase().matches("(?s).*(don't|do not|no)\\s+(reassure|advise|give me advice).*");
        boolean presentation = input.matches("(?s).*(展示|演示|汇报|答辩|上台|presentation|demo).*");
        boolean relationship = isRelationshipContext(input);
        boolean relationshipAmbiguity = relationship && protectsRelationshipAmbiguity(input);
        boolean pressure = input.matches("(?s).*(紧张|焦虑|压力|害怕|撑不住|累|慌).*");
        boolean english = input.codePoints()
                .noneMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        String repairedText;
        if (english && rejectsAdvice && presentation) {
            repairedText = "You are not asking for reassurance. One rough edge has become a verdict on whether the whole project has a soul.";
        } else if (english && rejectsAdvice) {
            repairedText = "You are not asking for an answer. You want the unsmoothed part of this to remain visible.";
        } else if (english && presentation) {
            repairedText = "The coming demo and this tension are now tied together. The sharpest part is still worth naming.";
        } else if (english && pressure) {
            repairedText = "The pressure has already entered the way you are holding yourself. The most concrete part can stay unsolved for a moment.";
        } else if (english) {
            repairedText = "There is still an unopened part in that sentence. You can continue from there.";
        } else if (singleAction) {
            repairedText = deterministicSingleAction(input);
        } else if (rejectsAdvice && presentation) {
            repairedText = "你不是来找答案的，只是想让“展示前很紧张”这件事有个落点。现在它有了。";
        } else if (rejectsAdvice && relationship) {
            repairedText = "先不判断谁对谁错。你刚才说的那一处关系卡点，可以原样留在这里。";
        } else if (rejectsAdvice) {
            repairedText = "你不是来找答案的，只是想让这句话有个落点。现在它有了。";
        } else if (presentation) {
            repairedText = "明天的展示和现在这份紧张已经连在一起了。最绷着你的那一处，还可以继续说。";
        } else if (relationshipAmbiguity && (input.contains("冷淡") || input.contains("很冷")
                || input.contains("变冷") || input.contains("冷下来"))) {
            repairedText = "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？";
        } else if (relationship) {
            repairedText = "这段关系里真正卡住你的那一处，比谁对谁错更值得先说。";
        } else if (pressure) {
            repairedText = "这份压力已经挤到你现在的状态里了。最具体的那一处，可以直接说。";
        } else {
            repairedText = "这句话里还有一处没有展开。你可以从那里继续。";
        }
        result.segments = List.of(repairedText);
        result.speakCount = 1;
        if (singleAction) {
            result.continueReason = "只给一个十分钟内可开始的动作";
            result.nextQuestion = "";
            result.smallStep = repairedText;
            result.featureSuggestion = "";
            result.featureTarget = "";
        } else if (rejectsAdvice) {
            result.nextQuestion = "";
            result.smallStep = "";
            result.featureSuggestion = "";
            result.featureTarget = "";
        }
        return result;
    }

    private static boolean isQuietDisclosure(String userInput) {
        String input = safe(userInput);
        boolean rejectsAdviceOrAnalysis = (input.contains("先别") || input.contains("不要") || input.contains("不用"))
                && (input.contains("建议") || input.contains("方案") || input.contains("分析")
                || input.contains("解决") || input.contains("拆解"));
        boolean onlyWantsToShare = (input.contains("只想") || input.contains("只是想"))
                && (input.contains("说出来") || input.contains("说一下") || input.contains("讲出来")
                || input.contains("表达") || input.contains("放在这里") || input.contains("告诉你"));
        return rejectsAdviceOrAnalysis && onlyWantsToShare;
    }

    private static boolean explicitlyRequestsRelationshipAdvice(String userInput) {
        String input = safe(userInput);
        return input.matches("(?s).*(怎么办|怎么做|我该怎么|应该怎么|要怎么|该不该|要不要"
                + "|给我.{0,6}建议|想听.{0,6}建议|帮我想想怎么|下一步|怎么回复|怎么回应).*");
    }

    private static boolean explicitlyRequestsSingleAction(String userInput) {
        String input = safe(userInput).replaceAll("\\s+", "");
        return input.matches("(?s).*(只|就)(拆|给|要|选|做|开始|推进|留下|保留|出).{0,12}(一|1)(步|个步骤|个动作|件事).*")
                || input.matches("(?s).*(一|1)(步|个步骤|个动作).{0,8}(就好|即可|够了|不要更多).*");
    }

    private static boolean violatesSingleActionScope(StructuredAiResults.AuroraResult result, String combined) {
        if (result == null || result.segments == null || result.segments.size() != 1) return true;
        if (safe(result.smallStep).isBlank()) return true;
        if (combined.contains("?") || combined.contains("？") || !safe(result.nextQuestion).isBlank()) return true;
        if (combined.matches("(?s).*(你可以选|任选|选择一个|选一个|要么|或者|还是先|想先).*")) return true;
        if (combined.matches("(?is).*(加一行注释|写一行注释|待修复|\\bTODO\\b).*")) return true;
        int originalTaskMentions = 0;
        for (String task : List.of("报告", "答辩", "代码", "PPT", "文档", "邮件", "作业")) {
            if (combined.contains(task)) originalTaskMentions++;
        }
        return originalTaskMentions >= 3;
    }

    private static String deterministicSingleAction(String userInput) {
        String input = safe(userInput);
        if (input.contains("报告")) {
            return "先打开报告文件，只写三行：要交付的结论、现有证据、还缺的一张截图；十分钟到就停。";
        }
        if (input.contains("答辩") || input.contains("展示") || input.contains("演示") || input.contains("PPT")) {
            return "先打开演示文稿，只写下观众离场前必须记住的一句话；十分钟到就停。";
        }
        if (input.contains("代码") || input.contains("bug") || input.contains("报错") || input.contains("修复")) {
            return "先打开最先失败的那条测试，只写下“实际结果”和“期望结果”各一行；十分钟到就停。";
        }
        return "先打开排在最前面的那一项，只写下它下一条可以看见的动作；十分钟到就停。";
    }

    private static boolean protectsRelationshipAmbiguity(String userInput) {
        String input = safe(userInput).replaceAll("\\s+", "");
        return input.contains("不想下结论")
                || input.contains("不想立刻")
                || input.contains("不知道是不是")
                || input.contains("还不确定")
                || input.contains("不能确定")
                || input.contains("不想猜")
                || input.contains("不想先猜")
                || input.contains("不想去猜")
                || input.contains("不愿意猜")
                || input.contains("先不猜")
                || input.contains("不想判断")
                || input.contains("不想贴标签")
                || input.contains("不想给他贴标签")
                || input.contains("不想给她贴标签");
    }

    private static boolean isRelationshipContext(String userInput) {
        String input = safe(userInput).replaceAll("\\s+", "");
        boolean hasRelationshipActor = input.contains("关系") || input.contains("朋友")
                || input.contains("同事") || input.contains("父母") || input.contains("伴侣")
                || input.contains("他") || input.contains("她");
        boolean hasRelationshipTension = input.contains("误解") || input.contains("争吵")
                || input.contains("难受") || input.contains("失望") || input.contains("生气")
                || input.contains("疏远") || input.contains("冷淡") || input.contains("很冷")
                || input.contains("变冷") || input.contains("冷下来");
        return hasRelationshipActor && hasRelationshipTension;
    }

    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }

    private static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    /**
     * Opaque, in-process continuation data for the post-turn inner-voice enrichment.
     * It must never be serialized or persisted.
     */
    public record InnerVoiceRequest(Long userId, String mode,
                                    StructuredAiResults.AuroraPlanResult plan,
                                    StructuredAiResults.AuroraResult spoken,
                                    LlmClient client) {}

    private record PlannerKey(long userId, String sessionId, String mode) {}
    private record StoredPlan(long revision, StructuredAiResults.AuroraPlanResult plan, long updatedAtEpochMs) {}
    private record PlannerRefreshResult(PlannerRunEvidence evidence, InnerVoiceRequest innerVoiceRequest) {}

    public enum PlannerStatus {
        SCHEDULED,
        RUNNING,
        SUCCEEDED,
        FALLBACK,
        FAILED
    }

    /** Safe, bounded evidence for the asynchronous deep kernel; contains no chain-of-thought. */
    public record PlannerRunEvidence(long revision, PlannerStatus status, String detail,
                                     long latencyMs, long updatedAtEpochMs) {}

    /** @param innerVoiceRequest null unless legacy same-turn planning produced it immediately. */
    public record Generation(StructuredAiResults.AuroraResult result, String runtime,
                              String relationshipMove, boolean repaired, List<String> criticIssues,
                              InnerVoiceRequest innerVoiceRequest,
                              CompletableFuture<InnerVoiceRequest> deferredInnerVoiceRequest,
                              Map<String, Long> stageLatenciesMs,
                              boolean plannerFallbackUsed, boolean speakerFallbackUsed,
                              boolean criticFallbackUsed, boolean backgroundPlannerScheduled,
                              String guidanceSource, String backgroundPlannerStatus,
                              CompletableFuture<PlannerRunEvidence> backgroundPlannerEvidence) {}
}
