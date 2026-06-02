package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.service.RelationNetworkService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for relationship network analysis.
 * B3: 关系网络 / Relation Temperature Map
 */
@RestController
@RequestMapping("/api/relation")
public class RelationNetworkController extends BaseController {

    private final RelationNetworkService relationNetworkService;

    public RelationNetworkController(RelationNetworkService relationNetworkService) {
        this.relationNetworkService = relationNetworkService;
    }

    @GetMapping("/list")
    public ApiResponse<List<RelationMention>> list(HttpSession session) {
        return ApiResponse.ok(relationNetworkService.findRelations(currentUserId(session)));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Integer>> stats(HttpSession session) {
        return ApiResponse.ok(relationNetworkService.getRelationStats(currentUserId(session)));
    }

    @GetMapping("/high-emotion")
    public ApiResponse<List<RelationMention>> highEmotion(HttpSession session) {
        return ApiResponse.ok(relationNetworkService.findHighEmotionRelations(currentUserId(session)));
    }

    @GetMapping("/timeline")
    public ApiResponse<List<RelationMention.TimelinePoint>> timeline(@RequestParam String label,
                                                                    HttpSession session) {
        return ApiResponse.ok(relationNetworkService.getRelationTimeline(currentUserId(session), label));
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health(@RequestParam String label, HttpSession session) {
        double score = relationNetworkService.calculateHealthScore(currentUserId(session), label);
        return ApiResponse.ok(Map.of("relationLabel", label, "healthScore", score));
    }

    @PostMapping("/extract/{memoryCardId}")
    public ApiResponse<Void> extract(@PathVariable Long memoryCardId, HttpSession session) {
        relationNetworkService.extractFromMemory(currentUserId(session), memoryCardId);
        return ApiResponse.<Void>ok(null);
    }
}
