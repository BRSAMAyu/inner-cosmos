package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.WeeklyReview;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.WeeklyReviewService;
import com.innercosmos.vo.DailyRecordVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/daily-record")
public class DailyRecordController extends BaseController {
    private final MemoryService memoryService;
    private final WeeklyReviewService weeklyReviewService;

    public DailyRecordController(MemoryService memoryService, WeeklyReviewService weeklyReviewService) {
        this.memoryService = memoryService;
        this.weeklyReviewService = weeklyReviewService;
    }

    @GetMapping("/latest")
    public ApiResponse<DailyRecordVO> latest(HttpSession session) {
        return ApiResponse.ok(memoryService.latestDailyRecord(currentUserId(session)));
    }

    @GetMapping("/date/{date}")
    public ApiResponse<DailyRecord> byDate(@PathVariable String date, HttpSession session) {
        return ApiResponse.ok(memoryService.dailyRecordByDate(currentUserId(session), date));
    }

    @PostMapping("/{id}/edit")
    public ApiResponse<DailyRecord> edit(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        return ApiResponse.ok(memoryService.editDailyRecord(
                currentUserId(session), id,
                body.get("theme"), body.get("emotionWeather"), body.get("cognitiveSummary")));
    }

    @GetMapping("/weekly/latest")
    public ApiResponse<WeeklyReview> weeklyLatest(HttpSession session) {
        return ApiResponse.ok(weeklyReviewService.latest(currentUserId(session)));
    }

    @PostMapping("/weekly/generate")
    public ApiResponse<WeeklyReview> weeklyGenerate(HttpSession session) {
        return ApiResponse.ok(weeklyReviewService.generateWeeklyReview(currentUserId(session)));
    }
}
