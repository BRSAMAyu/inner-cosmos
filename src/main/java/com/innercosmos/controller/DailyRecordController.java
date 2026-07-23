package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.WeeklyReview;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.WeeklyReviewService;
import com.innercosmos.service.WeeklyReviewV2Service;
import com.innercosmos.vo.DailyRecordVO;
import com.innercosmos.vo.WeeklyReviewV2VO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/daily-record")
public class DailyRecordController extends BaseController {
    private final MemoryService memoryService;
    private final WeeklyReviewService weeklyReviewService;
    private final WeeklyReviewV2Service weeklyReviewV2Service;

    public DailyRecordController(MemoryService memoryService,
                                 WeeklyReviewService weeklyReviewService,
                                 WeeklyReviewV2Service weeklyReviewV2Service) {
        this.memoryService = memoryService;
        this.weeklyReviewService = weeklyReviewService;
        this.weeklyReviewV2Service = weeklyReviewV2Service;
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

    // ── V2 endpoints ─────────────────────────────────────────────────────────

    @GetMapping("/weekly/v2/latest")
    public ApiResponse<WeeklyReviewV2VO> weeklyV2Latest(HttpSession session) {
        WeeklyReviewV2VO vo = weeklyReviewV2Service.latest(currentUserId(session));
        if (vo == null) {
            return ApiResponse.<WeeklyReviewV2VO>ok(null);
        }
        return ApiResponse.ok(vo);
    }

    @PostMapping("/weekly/v2/generate")
    public ApiResponse<WeeklyReviewV2VO> weeklyV2Generate(HttpSession session) {
        WeeklyReviewV2VO vo = weeklyReviewV2Service.generate(currentUserId(session));
        weeklyReviewV2Service.save(vo);
        return ApiResponse.ok(vo);
    }
}
