package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.ThemeAggregationService;
import com.innercosmos.vo.StarfieldDetailVO;
import com.innercosmos.vo.StarfieldVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController extends BaseController {
    private final MemoryService memoryService;
    private final ThemeAggregationService themeAggregationService;

    public MemoryController(MemoryService memoryService, ThemeAggregationService themeAggregationService) {
        this.memoryService = memoryService;
        this.themeAggregationService = themeAggregationService;
    }

    @PostMapping("/extract/{sessionId}")
    public ApiResponse<MemoryCard> extract(@PathVariable Long sessionId, HttpSession session) {
        return ApiResponse.ok(memoryService.extractFromSession(currentUserId(session), sessionId));
    }

    @GetMapping("/cards")
    public ApiResponse<List<MemoryCard>> cards(HttpSession session) {
        return ApiResponse.ok(memoryService.listCards(currentUserId(session)));
    }

    @GetMapping("/starfield")
    public ApiResponse<List<StarfieldVO>> starfield(HttpSession session) {
        return ApiResponse.ok(memoryService.starfield(currentUserId(session)));
    }

    @GetMapping("/starfield/{id}/detail")
    public ApiResponse<StarfieldDetailVO> starfieldDetail(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(memoryService.starfieldDetail(userId, id));
    }

    @PostMapping("/cards/{id}/importance")
    public ApiResponse<Void> updateImportance(@PathVariable Long id, @RequestBody Map<String, Double> body, HttpSession session) {
        Long userId = currentUserId(session);
        memoryService.updateImportance(userId, id, body.get("importance"));
        return ApiResponse.ok(null);
    }

    @PostMapping("/cards/{id}/archive")
    public ApiResponse<Void> archiveCard(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        memoryService.archiveCard(userId, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/themes")
    public ApiResponse<List<MemoryTheme>> themes(HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(themeAggregationService.findThemes(userId));
    }

    @GetMapping("/daily-records")
    public ApiResponse<List<DailyRecord>> dailyRecords(HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(memoryService.listDailyRecords(userId));
    }

    @PostMapping("/daily-records/{id}/accept")
    public ApiResponse<Void> acceptDailyRecord(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        memoryService.acceptDailyRecord(userId, id);
        return ApiResponse.ok(null);
    }
}
