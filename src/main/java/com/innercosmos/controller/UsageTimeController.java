package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.usage.UsageTimeService;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CP-08 使用时长: the user's own honest view of today's conversation time and a single
 * calm check-in when it grows large. Same shape as the quota view (transparent numbers +
 * reset basis), same tone rule as the relation review: a record, never a verdict — no
 * lockouts, no streaks, no judgemental copy.
 */
@RestController
public class UsageTimeController extends BaseController {

    private final UsageTimeService usageTime;

    public UsageTimeController(UsageTimeService usageTime) {
        this.usageTime = usageTime;
    }

    @GetMapping("/api/me/usage/today")
    public ApiResponse<Map<String, Object>> usageToday(HttpSession session) {
        UsageTimeService.UsageToday usage = usageTime.usageToday(currentUserId(session));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", usage.date());
        body.put("activeSeconds", usage.activeSeconds());
        body.put("turnCount", usage.turnCount());
        body.put("reminderAfterMinutes", usage.reminderAfterMinutes());
        body.put("reminderDue", usage.reminderDue());
        body.put("reminderNote", usage.reminderNote());
        body.put("basis", "COMPLETED_TURN_DURATION");
        return ApiResponse.ok(body);
    }
}
