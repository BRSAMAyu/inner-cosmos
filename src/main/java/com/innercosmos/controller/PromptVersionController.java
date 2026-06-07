package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.PromptTemplateEntity;
import com.innercosmos.service.PromptVersionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for prompt template version management.
 * D3: Prompt 版本管理
 */
@RestController
@RequestMapping("/api/prompt")
public class PromptVersionController extends BaseController {

    private final PromptVersionService promptVersionService;

    public PromptVersionController(PromptVersionService promptVersionService) {
        this.promptVersionService = promptVersionService;
    }

    @GetMapping("/active")
    public ApiResponse<Map<String, String>> active(@RequestParam String key, HttpSession session) {
        currentUserId(session);
        return ApiResponse.ok(Map.of("key", key, "content", promptVersionService.getActivePrompt(key)));
    }

    @PostMapping("/create")
    public ApiResponse<PromptTemplateEntity> create(@RequestParam String key,
                                                    @RequestParam String content,
                                                    @RequestParam(required = false, defaultValue = "") String description,
                                                    HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(promptVersionService.createPrompt(key, content, description));
    }

    @GetMapping("/versions")
    public ApiResponse<List<PromptTemplateEntity>> versions(@RequestParam String key) {
        return ApiResponse.ok(promptVersionService.listVersions(key));
    }

    @PostMapping("/rollback")
    public ApiResponse<PromptTemplateEntity> rollback(@RequestParam String key, @RequestParam int version, HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(promptVersionService.rollbackToVersion(key, version));
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled, HttpSession session) {
        requireAdmin(session);
        promptVersionService.toggleVersion(id, enabled);
        return ApiResponse.<Void>ok(null);
    }

    @GetMapping("/variant")
    public ApiResponse<PromptTemplateEntity> variant(@RequestParam String key, @RequestParam String variant) {
        return ApiResponse.ok(promptVersionService.getPromptVariant(key, variant));
    }

    @PostMapping("/record-metrics")
    public ApiResponse<Void> recordMetrics(@RequestParam String key,
                                           @RequestParam int version,
                                           @RequestParam double successRate,
                                           @RequestParam double avgLatency,
                                           HttpSession session) {
        requireAdmin(session);
        promptVersionService.recordMetrics(key, version, successRate, avgLatency);
        return ApiResponse.<Void>ok(null);
    }

    @GetMapping("/performance")
    public ApiResponse<Map<Integer, PromptVersionService.PromptMetrics>> performance(@RequestParam String key) {
        return ApiResponse.ok(promptVersionService.getPerformanceMetrics(key));
    }

    @GetMapping("/low-performing")
    public ApiResponse<List<PromptTemplateEntity>> lowPerforming(@RequestParam(defaultValue = "0.5") double threshold) {
        return ApiResponse.ok(promptVersionService.findLowPerformingPrompts(threshold));
    }
}
