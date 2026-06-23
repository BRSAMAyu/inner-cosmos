package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.BeliefPattern;
import com.innercosmos.service.BeliefExtractService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for belief pattern extraction and management.
 * B2: 信念识别 / Belief Halo
 */
@RestController
@RequestMapping("/api/belief")
public class BeliefController extends BaseController {

    private final BeliefExtractService beliefExtractService;

    public BeliefController(BeliefExtractService beliefExtractService) {
        this.beliefExtractService = beliefExtractService;
    }

    @GetMapping("/list")
    public ApiResponse<List<BeliefPattern>> list(HttpSession session) {
        return ApiResponse.ok(beliefExtractService.findBeliefs(currentUserId(session)));
    }

    @GetMapping("/by-category")
    public ApiResponse<List<BeliefPattern>> byCategory(@RequestParam String category, HttpSession session) {
        return ApiResponse.ok(beliefExtractService.findByCategory(currentUserId(session), category));
    }

    @GetMapping("/strong")
    public ApiResponse<List<BeliefPattern>> strong(@RequestParam(defaultValue = "0.5") double minStrength,
                                                    HttpSession session) {
        return ApiResponse.ok(beliefExtractService.findStrongBeliefs(currentUserId(session), minStrength));
    }

    @GetMapping("/contradictions")
    public ApiResponse<List<BeliefExtractService.ContradictionPair>> contradictions(HttpSession session) {
        return ApiResponse.ok(beliefExtractService.findContradictions(currentUserId(session)));
    }

    @PostMapping("/extract/{memoryCardId}")
    public ApiResponse<Void> extract(@PathVariable Long memoryCardId, HttpSession session) {
        beliefExtractService.extractFromMemory(currentUserId(session), memoryCardId);
        return ApiResponse.<Void>ok(null);
    }

    @PostMapping("/{beliefId}/recalculate")
    public ApiResponse<Void> recalculate(@PathVariable Long beliefId, HttpSession session) {
        beliefExtractService.recalculateStrength(currentUserId(session), beliefId); // M-078
        return ApiResponse.<Void>ok(null);
    }
}
