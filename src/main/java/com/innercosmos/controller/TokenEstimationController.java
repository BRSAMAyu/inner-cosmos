package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.TokenEstimationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for token estimation and LLM cost control.
 * D4: Token 估算 + 成本控制
 */
@RestController
@RequestMapping("/api/token")
public class TokenEstimationController extends BaseController {

    private final TokenEstimationService tokenEstimationService;

    public TokenEstimationController(TokenEstimationService tokenEstimationService) {
        this.tokenEstimationService = tokenEstimationService;
    }

    @GetMapping("/estimate")
    public ApiResponse<Map<String, Integer>> estimate(@RequestParam String text) {
        int tokens = tokenEstimationService.estimateTokens(text);
        return ApiResponse.ok(Map.of("text", tokens));
    }

    @PostMapping("/calculate")
    public ApiResponse<Map<String, Integer>> calculate(@RequestBody Map<String, String> body) {
        int tokens = tokenEstimationService.calculatePromptTokens(
                body.getOrDefault("system", ""),
                body.getOrDefault("user", ""));
        return ApiResponse.ok(Map.of("total", tokens));
    }

    @GetMapping("/response-estimate")
    public ApiResponse<Map<String, Integer>> responseEstimate(@RequestParam String prompt,
                                                            @RequestParam(defaultValue = "DAILY_TALK") String mode) {
        int tokens = tokenEstimationService.estimateResponseTokens(prompt, mode);
        return ApiResponse.ok(Map.of("estimated", tokens));
    }

    @GetMapping("/daily-usage")
    public ApiResponse<TokenEstimationService.TokenUsageStats> dailyUsage(HttpSession session) {
        return ApiResponse.ok(tokenEstimationService.getDailyUsage(currentUserId(session)));
    }

    @GetMapping("/within-budget")
    public ApiResponse<Map<String, Boolean>> withinBudget(HttpSession session) {
        return ApiResponse.ok(Map.of("within", tokenEstimationService.isWithinBudget(currentUserId(session))));
    }

    @GetMapping("/cost")
    public ApiResponse<Map<String, Double>> cost(@RequestParam int tokens,
                                                 @RequestParam(defaultValue = "default") String model) {
        return ApiResponse.ok(Map.of("cost", tokenEstimationService.estimateCost(tokens, model)));
    }

    @GetMapping("/forecast")
    public ApiResponse<TokenEstimationService.UsageForecast> forecast(HttpSession session) {
        return ApiResponse.ok(tokenEstimationService.getForecast(currentUserId(session)));
    }

    @PostMapping("/record")
    public ApiResponse<Void> record(@RequestParam String mode,
                                     @RequestParam int promptTokens,
                                     @RequestParam int responseTokens,
                                     HttpSession session) {
        tokenEstimationService.recordUsage(currentUserId(session), mode, promptTokens, responseTokens);
        return ApiResponse.<Void>ok(null);
    }
}
