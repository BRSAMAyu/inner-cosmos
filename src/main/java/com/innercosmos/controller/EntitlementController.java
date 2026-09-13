package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.payments.entitlement.EntitlementStateService;
import com.innercosmos.payments.entitlement.EntitlementStateService.EntitlementView;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CP-46 user-facing entitlement surface: the unified, cross-channel view (跨端权益) with
 * period end shown as the quota reset time (配额按已批准能力计量、显示剩余与重置时间 —
 * the window is the subscription period; per-capability metering lands with CP-17 quota
 * wiring), and the always-available cancellation entry (取消入口清晰 — 永不藏入口).
 * Payments can only arrive as verified server-side facts; this controller never accepts
 * a client-declared payment.
 */
@RestController
@RequestMapping("/api/me/entitlements")
public class EntitlementController extends BaseController {

    private final EntitlementStateService entitlementStateService;

    public EntitlementController(EntitlementStateService entitlementStateService) {
        this.entitlementStateService = entitlementStateService;
    }

    @GetMapping
    public ApiResponse<List<EntitlementView>> snapshot(HttpSession session) {
        return ApiResponse.ok(entitlementStateService.snapshot(currentUserId(session)));
    }

    /** 取消：auto-renew off, entitled until period end (user-visible 保留至到期). */
    @PostMapping("/{productId}/cancel")
    public ApiResponse<EntitlementView> cancel(@PathVariable String productId, HttpSession session) {
        Long userId = currentUserId(session);
        entitlementStateService.cancelByUser(userId, productId);
        return ApiResponse.ok(entitlementStateService.snapshot(userId).stream()
                .filter(view -> view.productId().equals(productId))
                .findFirst()
                .orElse(null));
    }
}
