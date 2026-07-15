package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.MemoryLifecycleService;
import com.innercosmos.service.StarfieldExplorerService;
import com.innercosmos.service.ThemeAggregationService;
import com.innercosmos.vo.StarfieldDetailVO;
import com.innercosmos.vo.StarfieldVO;
import com.innercosmos.vo.StarfieldSceneVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController extends BaseController {
    private final MemoryService memoryService;
    private final ThemeAggregationService themeAggregationService;
    private final StarfieldExplorerService starfieldExplorerService;
    private final MemoryLifecycleService memoryLifecycleService;

    public MemoryController(MemoryService memoryService, ThemeAggregationService themeAggregationService,
                            StarfieldExplorerService starfieldExplorerService,
                            MemoryLifecycleService memoryLifecycleService) {
        this.memoryService = memoryService;
        this.themeAggregationService = themeAggregationService;
        this.starfieldExplorerService = starfieldExplorerService;
        this.memoryLifecycleService = memoryLifecycleService;
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

    @GetMapping("/starfield/v2")
    public ApiResponse<StarfieldSceneVO> starfieldV2(
            @RequestParam(defaultValue = "TIME") String mode,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String layer,
            @RequestParam(required = false) String person,
            HttpSession session) {
        return ApiResponse.ok(starfieldExplorerService.explore(
                currentUserId(session), mode, query, layer, person));
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
        memoryLifecycleService.execute(userId, new MemoryOperationCommand(
                "ARCHIVE", id, null, null, null, null,
                "用户从记忆卡片归档", 1.0, "legacy:/api/memory/cards/{id}/archive"));
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
