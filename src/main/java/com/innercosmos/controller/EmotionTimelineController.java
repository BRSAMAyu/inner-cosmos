package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.EmotionTimeline;
import com.innercosmos.service.EmotionPatternService;
import com.innercosmos.service.EmotionTimelineService;
import com.innercosmos.vo.EmotionPatternVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for emotion timeline tracking and trend visualization.
 * B4: 情绪时间线 / Emotion Weather Line
 */
@RestController
@RequestMapping("/api/emotion/timeline")
public class EmotionTimelineController extends BaseController {

    private final EmotionTimelineService emotionTimelineService;
    private final EmotionPatternService emotionPatternService;

    public EmotionTimelineController(EmotionTimelineService emotionTimelineService,
                                    EmotionPatternService emotionPatternService) {
        this.emotionTimelineService = emotionTimelineService;
        this.emotionPatternService = emotionPatternService;
    }

    @GetMapping("/today")
    public ApiResponse<EmotionTimeline> today(HttpSession session) {
        return ApiResponse.ok(emotionTimelineService.getToday(currentUserId(session)));
    }

    @GetMapping("/range")
    public ApiResponse<List<EmotionTimeline>> range(@RequestParam String start,
                                                     @RequestParam String end,
                                                     HttpSession session) {
        return ApiResponse.ok(emotionTimelineService.getTimeline(
                currentUserId(session),
                LocalDate.parse(start),
                LocalDate.parse(end)));
    }

    @GetMapping("/trend")
    public ApiResponse<List<EmotionTimeline.TrendPoint>> trend(@RequestParam(defaultValue = "30") int days,
                                                                HttpSession session) {
        return ApiResponse.ok(emotionTimelineService.getTrend(currentUserId(session), days));
    }

    @GetMapping("/patterns")
    public ApiResponse<List<String>> patterns(@RequestParam(defaultValue = "30") int days,
                                               HttpSession session) {
        return ApiResponse.ok(emotionTimelineService.findDominantPatterns(currentUserId(session), days));
    }

    @GetMapping("/stability")
    public ApiResponse<Map<String, Object>> stability(@RequestParam(defaultValue = "30") int days,
                                                     HttpSession session) {
        double score = emotionTimelineService.calculateStability(currentUserId(session), days);
        return ApiResponse.ok(Map.of("days", days, "stabilityScore", score));
    }

    @PostMapping("/aggregate")
    public ApiResponse<Void> aggregate(@RequestParam String date, HttpSession session) {
        emotionTimelineService.aggregateForDate(currentUserId(session), LocalDate.parse(date));
        return ApiResponse.<Void>ok(null);
    }

    // ── EmotionPattern endpoints (M4) ─────────────────────────────────────

    @GetMapping("/patterns/v2")
    public ApiResponse<List<EmotionPatternVO>> patternsV2(@RequestParam(defaultValue = "30") int days,
                                                          HttpSession session) {
        return ApiResponse.ok(emotionPatternService.detectPatterns(currentUserId(session), days));
    }

    @GetMapping("/patterns/dominant")
    public ApiResponse<EmotionPatternVO> dominantPattern(@RequestParam(defaultValue = "30") int days,
                                                          HttpSession session) {
        EmotionPatternVO pattern = emotionPatternService.getDominantPattern(currentUserId(session), days);
        return ApiResponse.ok(pattern);
    }

    @GetMapping("/patterns/range")
    public ApiResponse<EmotionPatternVO> patternRange(@RequestParam String start,
                                                      @RequestParam String end,
                                                      HttpSession session) {
        return ApiResponse.ok(emotionPatternService.getRangeSummary(
                currentUserId(session),
                LocalDate.parse(start),
                LocalDate.parse(end)));
    }
}
