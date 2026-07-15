package com.innercosmos.ai.proactive;

import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.mapper.TodoItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Resolves 4-layer quiet window (quiet hours, sleep window, todo time blocks, focus windows).
 */
@Component
public class QuietWindowResolver {

    @Autowired
    private UserProfileMapper profileMapper;

    @Autowired
    private TodoItemMapper todoMapper;

    public record Reason(boolean quiet, String cause) {}

    public Reason canPushNow(Long userId, ZonedDateTime now) {
        if (userId == null || now == null || profileMapper == null) {
            return new Reason(false, "");
        }
        var profiles = profileMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserProfile>()
                .eq("user_id", userId).last("LIMIT 1"));
        var p = profiles.isEmpty() ? null : profiles.getFirst();
        if (p == null) return new Reason(false, "");
        LocalTime nowL = now.toLocalTime();

        // 1) quiet hours
        if (isInTimeWindow(nowL, p.quietHoursStart, p.quietHoursEnd)) {
            return new Reason(true, "quiet_hours");
        }

        // 2) sleep window
        if (isInTimeWindow(nowL, p.sleepWindowStart, p.sleepWindowEnd)) {
            return new Reason(true, "sleep");
        }

        // 3) todo time block
        if (todoMapper != null) {
            var todos = todoMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.innercosmos.entity.TodoItem>()
                    .eq("user_id", userId)
                    .eq("status", "PENDING")
            );
            for (var t : todos) {
                if (t.deadline != null) {
                    LocalTime start = t.deadline.toLocalTime().minusMinutes(30);
                    LocalTime end = t.deadline.toLocalTime();
                    if (!nowL.isBefore(start) && nowL.isBefore(end)) {
                        return new Reason(true, "todo:" + t.id);
                    }
                }
            }
        }

        // 4) focus window
        if (Boolean.TRUE.equals(p.focusModeEnabled) && p.focusWindowsJson != null) {
            var windows = parseWindows(p.focusWindowsJson);
            for (var w : windows) {
                if (!nowL.isBefore(w.start) && nowL.isBefore(w.end)) {
                    return new Reason(true, "focus");
                }
            }
        }

        return new Reason(false, "");
    }

    private boolean isInTimeWindow(LocalTime t, String startStr, String endStr) {
        if (startStr == null || endStr == null) return false;
        try {
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);
            return isInWindow(t, start, end);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isInWindow(LocalTime t, LocalTime start, LocalTime end) {
        if (start == null || end == null) return false;
        if (start.isBefore(end)) {
            return !t.isBefore(start) && t.isBefore(end);
        }
        // wraps midnight
        return !t.isBefore(start) || t.isBefore(end);
    }

    private record Window(LocalTime start, LocalTime end) {}

    private List<Window> parseWindows(String json) {
        // Simple JSON array parser for focus windows: [{"start":"10:00","end":"12:00"},...]
        if (json == null || json.isEmpty()) return List.of();
        List<Window> result = new java.util.ArrayList<>();
        try {
            // Simple regex-based parser
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\{\"start\":\"(\\d{2}:\\d{2})\"\\}", java.util.regex.Pattern.MULTILINE);
            java.util.regex.Matcher m = p.matcher(json);
            java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("\"end\":\"(\\d{2}:\\d{2})\"");
            java.util.regex.Matcher m2 = p2.matcher(json);
            while (m.find() && m2.find()) {
                String startStr = m.group(1);
                String endStr = m2.group(1);
                if (startStr != null && endStr != null) {
                    result.add(new Window(LocalTime.parse(startStr), LocalTime.parse(endStr)));
                }
            }
        } catch (Exception e) {
            // Return empty list on parse error
        }
        return result;
    }
}
