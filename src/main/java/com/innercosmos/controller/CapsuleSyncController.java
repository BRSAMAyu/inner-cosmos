package com.innercosmos.controller;

import com.innercosmos.ai.capsule.CapsuleSyncService;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.CapsuleSyncQueue;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for Capsule Sync queue management.
 * Allows users to review and approve/reject pending persona context updates.
 */
@RestController
@RequestMapping("/api/capsule/sync")
public class CapsuleSyncController extends BaseController {
    private final CapsuleSyncService syncService;

    public CapsuleSyncController(CapsuleSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * GET /api/capsule/sync/pending
     * Returns all pending sync queue entries for the current user.
     */
    @GetMapping("/pending")
    public ApiResponse<List<CapsuleSyncQueue>> pending(HttpSession session) {
        Long userId = currentUserId(session);
        List<CapsuleSyncQueue> entries = syncService.pending(userId);
        return ApiResponse.ok(entries);
    }

    /**
     * POST /api/capsule/sync/{id}/decide
     * Process user decision on a sync queue entry.
     *
     * @param id The queue entry ID
     * @param body Request body with decision (ALLOW/ALLOW_PARTIAL/REJECT) and optional allowedFields
     */
    @PostMapping("/{id}/decide")
    public ApiResponse<CapsuleSyncQueue> decide(@PathVariable Long id,
                                                @RequestBody java.util.Map<String, Object> body,
                                                HttpSession session) {
        Long userId = currentUserId(session);
        String decision = (String) body.getOrDefault("decision", "REJECT");
        List<String> allowedFields = null;
        Object raw = body.get("allowedFields");
        if (raw instanceof List<?> list) {
            allowedFields = list.stream().map(String::valueOf).toList();
        }
        CapsuleSyncQueue result = syncService.decide(userId, id, decision, allowedFields);
        return ApiResponse.ok(result);
    }

    /**
     * POST /api/capsule/sync/{id}/retry
     * IC-CAP-002 B-2: manually re-run a single FAILED sync row.
     */
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        syncService.retryFailed(userId, id);
        return ApiResponse.<Void>ok(null);
    }
}