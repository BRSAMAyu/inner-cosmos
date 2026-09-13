package com.innercosmos.controller;

import com.innercosmos.ai.observability.ProviderSpendGuard;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.payments.entitlement.EntitlementGates;
import com.innercosmos.payments.entitlement.EntitlementStateService;
import com.innercosmos.payments.entitlement.EntitlementStateService.EntitlementView;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CP-46 透明配额: what the user has used, what is left, and when each window resets —
 * the blueprint's 配额按已批准能力计量、显示剩余与重置时间. Two window kinds:
 * DAILY (AI 深度预算, resets at the spend guard's day rollover) and SUBSCRIPTION_PERIOD
 * (paid capability windows, reset at the entitlement period end). The never-pay-gated
 * list is returned verbatim as a standing promise (安全/纠正/导出删除永不付费解锁),
 * not as a quota to spend.
 */
@RestController
@RequestMapping("/api/me/quotas")
public class QuotaController extends BaseController {

    private final ProviderSpendGuard spendGuard;
    private final EntitlementStateService entitlements;

    public QuotaController(ProviderSpendGuard spendGuard,
                           EntitlementStateService entitlements) {
        this.spendGuard = spendGuard;
        this.entitlements = entitlements;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> quotas(HttpSession session) {
        long userId = currentUserId(session);
        ProviderSpendGuard.DailyQuota daily = spendGuard.dailyQuota(userId);

        Map<String, Object> aiQuota = new LinkedHashMap<>();
        aiQuota.put("capability", "ai.deep_daily_budget");
        aiQuota.put("basis", "DAILY");
        aiQuota.put("used", daily.usedCalls());
        aiQuota.put("limit", daily.callBudget());
        aiQuota.put("remaining", daily.remainingCalls());
        aiQuota.put("usedTokens", daily.usedTokens());
        aiQuota.put("limitTokens", daily.tokenBudget());
        aiQuota.put("resetsAt", daily.resetsAtUtc());

        List<Map<String, Object>> subscriptionWindows = entitlements.snapshot(userId).stream()
                .map(view -> {
                    Map<String, Object> window = new LinkedHashMap<String, Object>();
                    window.put("productId", view.productId());
                    window.put("capability", "memory.extended_horizon");
                    window.put("basis", "SUBSCRIPTION_PERIOD");
                    window.put("state", view.state());
                    window.put("resetsAt", view.quotaResetsAt());
                    window.put("autoRenew", view.autoRenew());
                    window.put("cancelAtPeriodEnd", view.cancelAtPeriodEnd());
                    return window;
                }).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quotas", List.of(aiQuota));
        body.put("subscriptionWindows", subscriptionWindows);
        body.put("neverPayGated", EntitlementGates.NEVER_PAID_GATED.stream().sorted().toList());
        return ApiResponse.ok(body);
    }
}
