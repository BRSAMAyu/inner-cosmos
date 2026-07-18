package com.innercosmos.ai.runtime;

import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.safety.CrisisKeywordRule;
import com.innercosmos.safety.DistressSignalDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Track A / A1 — per-turn adaptive dual-kernel budget decision.
 *
 * <p>TRACK-A-LIVING-INTELLIGENCE.md §5 requires the dual kernel to be <em>adaptive, not
 * mandatory</em>: "Simple turns should remain fast; high-ambiguity, high-continuity or high-risk
 * turns may spend more budget." Before this class, {@link AuroraDualKernelRuntime} only exposed a
 * global {@code inner-cosmos.aurora.runtime} switch (always single-pass or always dual-kernel for
 * every turn in a deployment) — see the A1 evidence README §5 "Adaptive dual-kernel budget
 * policy" gap. This class makes the decision per turn instead.
 *
 * <p>Deliberately reuses the existing risk classifiers in {@code com.innercosmos.safety}
 * ({@link CrisisKeywordRule}, {@link DistressSignalDetector}) rather than inventing a parallel
 * risk lexicon — those two already encode the product's tuned high-recall/low-false-positive
 * boundary between "genuine crisis language" and "casual venting" (see their own javadoc).
 *
 * <p>The policy is a small, additive, pure/stateless scorer: given a bundle of cheap per-turn
 * signals it returns a {@link Budget} plus the concrete reasons that drove it, so a decision is
 * always inspectable (never a silent black-box choice) and unit-testable with plain, deterministic
 * {@link Signals} values — no Spring context, no network, no LLM call.
 */
public class DualKernelBudgetPolicy {

    /** The two runtime budgets a turn can be routed to. */
    public enum Budget { SINGLE_PASS, DUAL_KERNEL }

    /** An inspectable decision: which budget, the raw score, and the reasons that produced it. */
    public record Decision(Budget budget, int score, List<String> reasons) {
        public boolean isDualKernel() {
            return budget == Budget.DUAL_KERNEL;
        }
    }

    /**
     * Deterministic, framework-free per-turn signal bundle. Kept independent of any live Spring
     * context or turn-assembly map so the scoring logic in {@link #decide(Signals)} can be unit
     * tested with hand-built values. {@link #from(Map)} adapts a real
     * {@code AuroraAgentServiceImpl} turn-context map (or a test harness's scripted context map,
     * e.g. {@code TrackARuntimeAblationEvaluationTest}'s {@code Scenario.context()}) onto this
     * shape.
     */
    public record Signals(String userMessage, boolean interruptionPresent, int relevantMemoryCount,
                          int recentThreadDepth) {

        public static Signals simple(String userMessage) {
            return new Signals(userMessage, false, 0, 0);
        }

        /**
         * Extracts signals from a real (or scripted) turn-context map. Understands both the
         * production shape (an {@link AgentContext} instance under {@code unifiedAgentContext})
         * and the simpler direct keys ({@code relevantMemoryIds}, {@code interruptionContext})
         * already used by the existing offline ablation scripts, so both production and the
         * scenario-catalog-driven test harness can share this one extraction path.
         */
        public static Signals from(Map<String, Object> context) {
            if (context == null) return simple("");
            String userMessage = stringOf(context.get("userMessage"));
            Object interruption = context.get("interruptionContext");
            boolean interruptionPresent = interruption != null && !String.valueOf(interruption).isBlank();
            return new Signals(userMessage, interruptionPresent, relevantMemoryCount(context),
                    recentThreadDepth(context));
        }

        private static int relevantMemoryCount(Map<String, Object> context) {
            Object direct = context.get("relevantMemoryIds");
            if (direct instanceof List<?> list) return list.size();
            Object unified = context.get("unifiedAgentContext");
            if (unified instanceof AgentContext agentContext && agentContext.evidenceMemoryIds != null) {
                return agentContext.evidenceMemoryIds.size();
            }
            return 0;
        }

        private static int recentThreadDepth(Map<String, Object> context) {
            Object unified = context.get("unifiedAgentContext");
            if (unified instanceof AgentContext agentContext && agentContext.recentMessages != null) {
                return agentContext.recentMessages.size();
            }
            return 0;
        }

        private static String stringOf(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }

    /** Score at or above this threshold routes the turn to the dual-kernel plan-then-speak(-then-critic) path. */
    static final int DUAL_KERNEL_THRESHOLD = 2;

    private static final int RISK_WEIGHT = 3;
    private static final int AMBIGUITY_WEIGHT = 2;
    private static final int INTERRUPTION_WEIGHT = 2;
    private static final int MEMORY_WEIGHT_PER_ITEM = 1;
    private static final int MEMORY_WEIGHT_CAP = 2;
    private static final int THREAD_DEPTH_WEIGHT = 1;
    /** A recent-message count at/above this is treated as an established, multi-turn thread. */
    private static final int THREAD_DEPTH_TRIGGER = 6;

    /**
     * Ambiguity markers: phrases where the user explicitly signals they are not sure what they
     * need (advice vs. company, this vs. that), as opposed to a plainly stated request or
     * observation. Kept as a small, tight, dedicated list (distinct from the safety lexicons,
     * which are about risk, not ambiguity) to avoid false positives on ordinary declarative text.
     */
    private static final List<String> AMBIGUITY_MARKERS = List.of(
            "说不清楚", "说不清", "不清楚", "不确定", "说不好", "说不上来", "拿不准",
            "不知道该", "不知道是不是", "还是只是"
    );

    private final CrisisKeywordRule crisisKeywordRule = new CrisisKeywordRule();
    private final DistressSignalDetector distressSignalDetector = new DistressSignalDetector();

    /** Convenience entry point used by {@link AuroraDualKernelRuntime} for a real turn context. */
    public Decision decide(Map<String, Object> turnContext) {
        return decide(Signals.from(turnContext));
    }

    /** The core, deterministic scoring logic — see class javadoc for the design rationale. */
    public Decision decide(Signals signals) {
        List<String> reasons = new ArrayList<>();
        int score = 0;
        String message = signals.userMessage() == null ? "" : signals.userMessage();

        boolean riskKeyword = crisisKeywordRule.match(message).matched;
        boolean distress = !riskKeyword && distressSignalDetector.hasDistressSignal(message);
        if (riskKeyword) {
            score += RISK_WEIGHT;
            reasons.add("risk:crisis_keyword");
        } else if (distress) {
            score += RISK_WEIGHT;
            reasons.add("risk:distress_signal");
        }

        if (containsAmbiguityMarker(message)) {
            score += AMBIGUITY_WEIGHT;
            reasons.add("ambiguity:marker");
        }

        if (signals.interruptionPresent()) {
            score += INTERRUPTION_WEIGHT;
            reasons.add("continuity:interruption");
        }

        if (signals.relevantMemoryCount() > 0) {
            int weight = Math.min(signals.relevantMemoryCount() * MEMORY_WEIGHT_PER_ITEM, MEMORY_WEIGHT_CAP);
            score += weight;
            reasons.add("continuity:relevant_memories=" + signals.relevantMemoryCount());
        }

        if (signals.recentThreadDepth() >= THREAD_DEPTH_TRIGGER) {
            score += THREAD_DEPTH_WEIGHT;
            reasons.add("continuity:established_thread_depth=" + signals.recentThreadDepth());
        }

        Budget budget = score >= DUAL_KERNEL_THRESHOLD ? Budget.DUAL_KERNEL : Budget.SINGLE_PASS;
        if (reasons.isEmpty()) reasons.add("simple_turn:no_risk_ambiguity_or_continuity_signal");
        return new Decision(budget, score, List.copyOf(reasons));
    }

    private static boolean containsAmbiguityMarker(String message) {
        for (String marker : AMBIGUITY_MARKERS) {
            if (message.contains(marker)) return true;
        }
        return false;
    }
}
