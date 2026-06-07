package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.ABTestConfig;
import com.innercosmos.service.ABTestService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for A/B testing traffic splitting between MOCK and REMOTE LLM providers.
 * D5: A/B Testing Framework
 */
@RestController
@RequestMapping("/api/abtest")
public class ABTestController extends BaseController {

    private final ABTestService abTestService;

    public ABTestController(ABTestService abTestService) {
        this.abTestService = abTestService;
    }

    @GetMapping("/active")
    public ApiResponse<ABTestConfig> activeConfig(HttpSession session) {
        currentUserId(session);
        return ApiResponse.ok(abTestService.getActiveConfig());
    }

    @PostMapping("/config")
    public ApiResponse<ABTestConfig> saveConfig(@RequestBody ABTestConfig config, HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(abTestService.saveConfig(config));
    }

    @GetMapping("/assign")
    public ApiResponse<Map<String, String>> assign(@RequestParam String module, HttpSession session) {
        String group = abTestService.assignGroup(currentUserId(session), module);
        return ApiResponse.ok(Map.of("module", module, "group", group));
    }

    @PostMapping("/metrics")
    public ApiResponse<Void> recordMetrics(@RequestParam String module,
                                            @RequestParam double latency,
                                            @RequestParam boolean success,
                                            @RequestParam boolean fallback,
                                            HttpSession session) {
        String group = abTestService.getUserGroup(currentUserId(session), module);
        abTestService.recordMetrics(currentUserId(session), group, module, latency, success, fallback);
        return ApiResponse.<Void>ok(null);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, ABTestService.ABTestStats>> stats(@RequestParam String testName, HttpSession session) {
        currentUserId(session);
        return ApiResponse.ok(abTestService.getAggregatedStats(testName));
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled, HttpSession session) {
        requireAdmin(session);
        abTestService.toggleTest(id, enabled);
        return ApiResponse.<Void>ok(null);
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<ABTestService.ABTestReport> complete(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(abTestService.completeTest(id));
    }
}
