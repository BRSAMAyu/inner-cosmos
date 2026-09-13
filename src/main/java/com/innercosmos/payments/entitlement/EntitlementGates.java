package com.innercosmos.payments.entitlement;

import java.util.Set;

/**
 * CP-46 structural guarantee: 安全、纠正、导出与删除能力永远不进入付费墙 —
 * the blueprint's "安全／纠正／导出删除永不付费解锁" is a catalog contract, not a promise.
 * Anything in {@link #NEVER_PAID_GATED} can never appear in {@link #PAID_GATED}; the
 * contract test enforces the two sets stay disjoint and that every safety-family
 * capability is in the never-gated set. Paid gating is reserved for capacity comforts
 * (deeper AI budgets, longer memory horizons), never for dignity or rights.
 */
public final class EntitlementGates {

    /** Capability slugs the product may tie to a paid entitlement. */
    public static final Set<String> PAID_GATED = Set.of(
            "ai.deep_daily_budget",       // 更高的每日 AI 深度预算
            "memory.extended_horizon"     // 更长的记忆回看窗口
    );

    /** Capability slugs that must NEVER be behind any payment, now or in any future edit. */
    public static final Set<String> NEVER_PAID_GATED = Set.of(
            "safety.crisis_interception", // 危机拦截与安全资源
            "safety.boundary_controls",   // 边界与屏蔽设置
            "portrait.correction",        // 画像纠正（"这不太是我"）
            "memory.suppress_and_delete", // 记忆搁置与删除
            "data.export",                // 数据导出
            "data.deletion",              // 账号与数据删除
            "account.cancellation",       // 注销
            "support.basic_ticket"        // 基础客服工单
    );

    private EntitlementGates() {
    }
}
