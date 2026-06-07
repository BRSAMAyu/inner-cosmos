package com.innercosmos.ai.perception;

import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Time context for the agent: which part of the day it is, a friendly date label,
 * whether the user is in their inferred sleep window, and (optionally) the nearest
 * open todo. Pure local computation — no external API, no LLM.
 * <p>
 * Sleep inference: 23:00–07:00 local. During the sleep window the agent should
 * soften its voice and prefer "settle" actions over new questions.
 * <p>
 * Focus is intentionally not auto-detected: callers (e.g. focus-mode aware UI)
 * pass it in via {@link #now(boolean, String)}.
 */
@Service
public class TimeContextService {

    /**
     * Snapshot of the user's temporal context.
     *
     * @param label       one of 清晨/早晨/上午/中午/下午/傍晚/晚上/深夜
     * @param dateLabel   human-readable "M月d日 EEE HH:mm" in the system locale's Chinese formatting
     * @param isSleep     true if the current hour is in the inferred sleep window (23:00–07:00)
     * @param isFocus     true if a focus window is currently active (passed in by caller)
     * @param nearestTodo title of the nearest open todo, or null if none / not provided
     */
    public record TimeContext(String label, String dateLabel, boolean isSleep, boolean isFocus, String nearestTodo) {}

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M月d日 EEE HH:mm", Locale.CHINA);

    /**
     * Current time context, default focus=false, no nearest todo.
     */
    public TimeContext now() {
        return now(false, null);
    }

    /**
     * Current time context with caller-provided focus state and nearest todo.
     */
    public TimeContext now(boolean focusActive, String nearestTodo) {
        ZonedDateTime n = ZonedDateTime.now(ZoneId.systemDefault());
        String label = timeLabel(n.getHour());
        return new TimeContext(label, n.format(DATE_FORMAT), isInferredSleep(n), focusActive, nearestTodo);
    }

    /**
     * Map an hour-of-day to a Chinese time-of-day label. The same thresholds
     * the agent prompt has been using informally for greeting — formalised here
     * so the front-end and the agent see the exact same string.
     */
    public static String timeLabel(int h) {
        if (h >= 5 && h < 7) return "清晨";
        if (h >= 7 && h < 9) return "早晨";
        if (h >= 9 && h < 12) return "上午";
        if (h >= 12 && h < 14) return "中午";
        if (h >= 14 && h < 18) return "下午";
        if (h >= 18 && h < 20) return "傍晚";
        if (h >= 20 && h < 23) return "晚上";
        return "深夜";
    }

    /**
     * Inferred sleep window: 23:00–07:00 local. We treat the window as inclusive
     * at the start (23:00 = sleep) and exclusive at the end (07:00 = morning).
     */
    public static boolean isInferredSleep(ZonedDateTime n) {
        int h = n.getHour();
        return h >= 23 || h < 7;
    }
}
