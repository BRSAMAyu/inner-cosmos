package com.innercosmos.ai.router;

/**
 * CP-19 deterministic kernel routing: the slow kernel (planner/speaker/critic) is reserved
 * for turns that genuinely need it — complexity or risk — while simple turns stay on the
 * single fast path. "无增益的额外阶段不默认启用": extra stages are opt-in by evidence
 * (the registered CP-19 experiment), never a default. Crisis turns never route to deep
 * analysis: they route to the support flow (CP-20).
 */
public final class KernelRoutingPolicy {

    public enum Kernel { SINGLE, DUAL, SUPPORT_FLOW }

    public record TurnSignals(int inputLength, boolean userAskedForAnalysis,
                              boolean multiPartRequest, boolean riskContext, boolean crisisHit) {
    }

    private static final int LENGTH_THRESHOLD = 160;

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
