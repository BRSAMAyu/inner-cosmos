package com.innercosmos.ai.perception;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds a user's temporal context from the application's authoritative instant.
 *
 * <p>The current request's valid IANA timezone wins for this conversational turn because it
 * reflects travel or a device timezone change immediately. The persisted profile timezone is
 * the trusted fallback, followed by {@value #DEFAULT_ZONE}. Neither a client-rendered time nor
 * {@code localTimeLabel} is ever used as the current instant.</p>
 */
@Service
public class TimeContextService {

    public static final String DEFAULT_ZONE = "Asia/Shanghai";
    public static final String DEFAULT_LOCALE = "zh-CN";

    private static final DateTimeFormatter ZH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.SIMPLIFIED_CHINESE);
    private static final DateTimeFormatter EN_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm", Locale.forLanguageTag("en-SG"));

    private final Clock clock;

    public TimeContextService(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param label time-of-day label in the selected locale
     * @param dateLabel localized date, weekday and local wall time
     * @param zoneId resolved IANA timezone
     * @param localeTag normalized supported locale ({@code zh-CN} or {@code en-SG})
     * @param isSleep whether local time is in the inferred 23:00-07:00 sleep window
     * @param isFocus caller-supplied focus state (kept for backwards compatibility)
     * @param nearestTodo nearest open todo title, if available
     * @param lastInteractionLabel localized elapsed time since the prior Aurora reply, or empty
     * @param clientTimeHintStatus validation result only; client text never changes server time
     * @param localDateTime ISO local date-time including numeric offset for structured grounding
     */
    public record TimeContext(
            String label,
            String dateLabel,
            String zoneId,
            String localeTag,
            boolean isSleep,
            boolean isFocus,
            String nearestTodo,
            String lastInteractionLabel,
            String clientTimeHintStatus,
            String localDateTime) {

        /** Compatibility constructor for lightweight tests and older callers. */
        public TimeContext(String label, String dateLabel, boolean isSleep,
                           boolean isFocus, String nearestTodo) {
            this(label, dateLabel, DEFAULT_ZONE, DEFAULT_LOCALE, isSleep, isFocus,
                    nearestTodo, "", "NOT_PROVIDED", "");
        }
    }

    public TimeContext now() {
        return now(null, null, null, null, false, null, null);
    }

    public TimeContext now(boolean focusActive, String nearestTodo) {
        return now(null, null, null, null, focusActive, nearestTodo, null);
    }

    public TimeContext now(String requestTimezone,
                           String profileTimezone,
                           String locale,
                           String clientLocalTimeLabel,
                           boolean focusActive,
                           String nearestTodo,
                           Instant previousInteraction) {
        ZoneId zone = resolveZone(requestTimezone, profileTimezone);
        String localeTag = normalizeLocale(locale);
        ZonedDateTime localNow = ZonedDateTime.ofInstant(clock.instant(), zone);
        String label = timeLabel(localNow.getHour(), localeTag);
        String dateLabel = localNow.format(isEnglish(localeTag) ? EN_DATE_FORMAT : ZH_DATE_FORMAT);
        return new TimeContext(
                label,
                dateLabel,
                zone.getId(),
                localeTag,
                isInferredSleep(localNow),
                focusActive,
                nearestTodo,
                elapsedLabel(previousInteraction, clock.instant(), localeTag),
                clientHintStatus(clientLocalTimeLabel, label),
                localNow.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    /**
     * Resolve only syntactically valid IANA region IDs. Fixed offsets and short aliases are not
     * accepted because user schedules and DST behavior require a stable region timezone.
     */
    public static ZoneId resolveZone(String requestTimezone, String profileTimezone) {
        ZoneId request = validIanaZone(requestTimezone);
        if (request != null) return request;
        ZoneId profile = validIanaZone(profileTimezone);
        return profile == null ? ZoneId.of(DEFAULT_ZONE) : profile;
    }

    private static ZoneId validIanaZone(String candidate) {
        if (candidate == null || candidate.isBlank() || !candidate.contains("/")) return null;
        try {
            return ZoneId.of(candidate.trim());
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public static String normalizeLocale(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en")
                ? "en-SG" : DEFAULT_LOCALE;
    }

    public static String timeLabel(int hour) {
        return timeLabel(hour, DEFAULT_LOCALE);
    }

    public static String timeLabel(int hour, String locale) {
        boolean english = isEnglish(locale);
        if (hour >= 5 && hour < 7) return english ? "dawn" : "清晨";
        if (hour >= 7 && hour < 9) return english ? "early morning" : "早晨";
        if (hour >= 9 && hour < 12) return english ? "morning" : "上午";
        if (hour >= 12 && hour < 14) return english ? "noon" : "中午";
        if (hour >= 14 && hour < 18) return english ? "afternoon" : "下午";
        if (hour >= 18 && hour < 20) return english ? "evening" : "傍晚";
        if (hour >= 20 && hour < 23) return english ? "night" : "晚上";
        return english ? "late night" : "深夜";
    }

    public static boolean isInferredSleep(ZonedDateTime localTime) {
        int hour = localTime.getHour();
        return hour >= 23 || hour < 7;
    }

    private static String elapsedLabel(Instant previous, Instant now, String locale) {
        if (previous == null || now == null || previous.isAfter(now.plusSeconds(60))) return "";
        Duration elapsed = Duration.between(previous, now);
        long minutes = elapsed.toMinutes();
        boolean english = isEnglish(locale);
        if (minutes < 1) return english ? "just now" : "刚刚";
        if (minutes < 60) return english ? minutes + " minutes ago" : minutes + "分钟前";
        long hours = elapsed.toHours();
        if (hours < 24) return english ? hours + " hours ago" : hours + "小时前";
        long days = elapsed.toDays();
        return english ? days + " days ago" : days + "天前";
    }

    private static String clientHintStatus(String clientLabel, String authoritativeLabel) {
        if (clientLabel == null || clientLabel.isBlank()) return "NOT_PROVIDED";
        return clientLabel.trim().equalsIgnoreCase(authoritativeLabel)
                ? "MATCHED" : "MISMATCH_IGNORED";
    }

    private static boolean isEnglish(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en");
    }
}
