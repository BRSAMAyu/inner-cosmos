package com.innercosmos.service;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small deterministic negotiation grammar; ambiguous DST wall times are rejected by WakeIntentService. */
@Component
public class NaturalTimeNegotiator {
    private static final Pattern RELATIVE_ZH = Pattern.compile("(\\d+)\\s*(分钟|小时)后");
    private static final Pattern RELATIVE_EN = Pattern.compile("in\\s+(\\d+)\\s*(minutes?|hours?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK = Pattern.compile("(?:(?:at|上午|下午|晚上|今晚|明天|tomorrow)\\s*)?(\\d{1,2})(?:[:：点](\\d{1,2}))?", Pattern.CASE_INSENSITIVE);

    public Window negotiate(String expression, ZoneId zone) {
        String raw = expression == null ? "" : expression.trim();
        if (raw.isBlank()) throw bad("when is required");
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDateTime preferred;
        Matcher zh = RELATIVE_ZH.matcher(raw);
        Matcher en = RELATIVE_EN.matcher(raw.toLowerCase(Locale.ROOT));
        if (zh.find()) {
            long amount = Long.parseLong(zh.group(1));
            preferred = ("小时".equals(zh.group(2)) ? now.plusHours(amount) : now.plusMinutes(amount)).toLocalDateTime();
        } else if (en.find()) {
            long amount = Long.parseLong(en.group(1));
            preferred = (en.group(2).toLowerCase(Locale.ROOT).startsWith("hour") ? now.plusHours(amount) : now.plusMinutes(amount)).toLocalDateTime();
        } else {
            boolean tomorrow = raw.contains("明天") || raw.toLowerCase(Locale.ROOT).contains("tomorrow") || raw.contains("明早");
            boolean tonight = raw.contains("今晚");
            Matcher clock = CLOCK.matcher(raw);
            int hour = raw.contains("明早") ? 8 : tonight ? 21 : -1;
            int minute = raw.contains("明早") ? 30 : 0;
            if (clock.find()) {
                hour = Integer.parseInt(clock.group(1));
                if (clock.group(2) != null) minute = Integer.parseInt(clock.group(2));
                if ((raw.contains("下午") || raw.contains("晚上")) && hour < 12) hour += 12;
            }
            if (hour < 0 || hour > 23 || minute > 59) throw bad("无法确定时间，请尝试“明天 8:30”或“2 小时后”");
            LocalDate date = now.toLocalDate().plusDays(tomorrow ? 1 : 0);
            preferred = LocalDateTime.of(date, LocalTime.of(hour, minute));
            if (!tomorrow && !preferred.isAfter(now.toLocalDateTime())) {
                if (tonight) throw bad("今晚这个时间已经过去，请换一个时间");
                preferred = preferred.plusDays(1);
            }
        }
        return new Window(preferred.minusMinutes(10), preferred, preferred.plusHours(3), raw);
    }

    public record Window(LocalDateTime earliestAt, LocalDateTime preferredAt,
                         LocalDateTime latestAt, String understoodAs) {}

    private static BusinessException bad(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
