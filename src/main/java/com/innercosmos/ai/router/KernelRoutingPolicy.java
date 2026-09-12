package com.innercosmos.ai.router;

import com.innercosmos.safety.CrisisKeywordRule;
import com.innercosmos.safety.DistressSignalDetector;

import java.util.Map;

/**
 * CP-19 deterministic kernel routing: the slow kernel (planner/speaker/critic) is reserved
 * for turns that genuinely need it — complexity or risk — while simple turns stay on the
 * single fast path. "无增益的额外阶段不默认启用": extra stages are opt-in by evidence
 * (the registered CP-19 experiment), never a default. Crisis turns never route to deep
 * analysis: they route to the support flow (CP-20).
 *
 * <p>The production signal extraction lives in {@link TurnSignals#from(Map)} and deliberately
 * reuses the same {@code com.innercosmos.safety} classifiers as
 * {@link com.innercosmos.ai.runtime.DualKernelBudgetPolicy} — one tuned crisis/distress
 * boundary, no parallel lexicon. What this policy adds on top of the budget scorer is the
 * complexity axis (length / explicit analysis ask / multi-part requests) and the explicit
 * {@link Kernel#SUPPORT_FLOW} outcome, which the runtime maps onto the dual path's
 * safetyContract (gentle check-in / resource offer) rather than an analysis-first plan.
 */
public final class KernelRoutingPolicy {

    public enum Kernel { SINGLE, DUAL, SUPPORT_FLOW }

    public record TurnSignals(int inputLength, boolean userAskedForAnalysis,
                              boolean multiPartRequest, boolean riskContext, boolean crisisHit) {

        /**
         * Extracts the five routing signals from a real turn-context map (the same map
         * {@code AuroraAgentServiceImpl} assembles and {@code AuroraDualKernelRuntime}
         * routes on). Deterministic, cheap and side-effect free — usable on every turn.
         */
        public static TurnSignals from(Map<String, Object> context) {
            if (context == null) return new TurnSignals(0, false, false, false, false);
            Object raw = context.get("userMessage");
            String message = raw == null ? "" : String.valueOf(raw);
            boolean crisis = CRISIS_RULE.match(message).matched;
            boolean distress = !crisis && DISTRESS_DETECTOR.hasDistressSignal(message);
            return new TurnSignals(message.length(), askedForAnalysis(message),
                    multiPart(message), distress, crisis);
        }

        private static boolean askedForAnalysis(String message) {
            for (String marker : ANALYSIS_ASK_MARKERS) {
                if (message.contains(marker)) return true;
            }
            return false;
        }

        private static boolean multiPart(String message) {
            int questionMarks = 0;
            for (int i = 0; i < message.length(); i++) {
                char c = message.charAt(i);
                if (c == '？' || c == '?') questionMarks++;
            }
            if (questionMarks >= 2) return true;
            for (String marker : MULTI_PART_MARKERS) {
                if (message.contains(marker)) return true;
            }
            return false;
        }
    }

    private static final int LENGTH_THRESHOLD = 160;

    /**
     * Explicit analysis asks only. Deliberately tighter than generic curiosity words so an
     * ordinary action request ("帮我把这份报告拆成能开始的第一步") stays a simple turn.
     */
    private static final String[] ANALYSIS_ASK_MARKERS = {
            "帮我分析", "分析一下", "帮我梳理", "梳理一下", "帮我理清", "理清一下",
            "怎么看待", "帮我看看这背后", "为什么会这样"
    };

    /** Second-part discourse connectors; "还有" alone is too common in single-part speech. */
    private static final String[] MULTI_PART_MARKERS = {
            "另外一件事", "还有一件事", "另一件事", "第二个问题", "顺便问一下", "顺便问下",
            "再者", "其次呢"
    };

    private static final CrisisKeywordRule CRISIS_RULE = new CrisisKeywordRule();
    private static final DistressSignalDetector DISTRESS_DETECTOR = new DistressSignalDetector();

    private KernelRoutingPolicy() {
    }

    /** Pure decision function — unit-tested, no I/O, no model calls. */
    public static Kernel route(TurnSignals signals) {
        if (signals == null) {
            return Kernel.SINGLE;
        }
        if (signals.crisisHit()) {
            // A crisis never earns "deep analysis": it routes to support, immediately.
            return Kernel.SUPPORT_FLOW;
        }
        if (signals.riskContext()) {
            // Elevated risk context keeps the careful dual path with critic oversight.
            return Kernel.DUAL;
        }
        boolean complex = signals.inputLength() >= LENGTH_THRESHOLD
                || signals.userAskedForAnalysis()
                || signals.multiPartRequest();
        return complex ? Kernel.DUAL : Kernel.SINGLE;
    }
}
