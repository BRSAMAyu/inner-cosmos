package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * CP-26 quiet hours (免打扰时段). Resolves the platform default window
 * ({@code inner-cosmos.wake.quiet-hours.*}) together with the user's own quiet hours
 * ({@code UserProfile.quietHoursStart/End}) into one decision: whether "now" sits inside a
 * do-not-disturb window and, when it does, the UTC instant that window ends — so a due wake
 * intent can be DEFERRED (visibly, with deferred_until) until then instead of delivered at
 * night or silently re-polled.
 *
 * <p>Both windows are daily wall-clock windows in the user's timezone and may wrap midnight
 * (e.g. 22:00–07:00). When both are active at once the <em>later</em> end wins, so the intent
 * never resumes while any quiet window is still open. The platform window only takes effect
 * when explicitly enabled (default off = legacy behaviour unchanged); a malformed window is
 * treated as absent rather than guessed.</p>
 */
@Component
public class WakeIntentQuietHoursPolicy {
    private final boolean enabled;
    private final LocalTime platformStart;
    private final LocalTime platformEnd;
    private final UserProfileMapper profiles;

    public WakeIntentQuietHoursPolicy(
            @Value("${inner-cosmos.wake.quiet-hours.enabled:false}") boolean enabled,
            @Value("${inner-cosmos.wake.quiet-hours.start:22:00}") String start,
            @Value("${inner-cosmos.wake.quiet-hours.end:07:00}") String end,
            UserProfileMapper profiles) {
        this.enabled = enabled;
        this.platformStart = parse(start);
        this.platformEnd = parse(end);
        this.profiles = profiles;
    }

    /** quiet=true means "hold delivery"; deferUntilUtc is the UTC instant the window ends. */
    public record Decision(boolean quiet, String cause, LocalDateTime deferUntilUtc) {
        public static Decision open() {
            return new Decision(false, "", null);
        }
    }

    public Decision evaluate(Long userId, ZonedDateTime nowInZone) {
        if (nowInZone == null) return Decision.open();
        LocalDateTime deferUntilUtc = null;
        String cause = null;
        if (enabled) {
            Optional<LocalDateTime> platformWindowEnd = windowEndUtc(platformStart, platformEnd, nowInZone);
            if (platformWindowEnd.isPresent()) {
                deferUntilUtc = platformWindowEnd.get();
                cause = "platform_quiet_hours";
            }
        }
        Optional<LocalDateTime> userWindowEnd = userWindowEndUtc(userId, nowInZone);
        if (userWindowEnd.isPresent() && (deferUntilUtc == null || userWindowEnd.get().isAfter(deferUntilUtc))) {
            deferUntilUtc = userWindowEnd.get();
            cause = "user_quiet_hours";
        }
        return deferUntilUtc == null ? Decision.open() : new Decision(true, cause, deferUntilUtc);
    }

    /** The user-configured quiet hours; users without a profile (or an unparseable window) are not quieted. */
    private Optional<LocalDateTime> userWindowEndUtc(Long userId, ZonedDateTime nowInZone) {
        if (userId == null || profiles == null) return Optional.empty();
        var rows = profiles.selectList(new QueryWrapper<UserProfile>()
            .eq("user_id", userId).last("LIMIT 1"));
        if (rows.isEmpty()) return Optional.empty();
        UserProfile profile = rows.getFirst();
        return windowEndUtc(parse(profile.quietHoursStart), parse(profile.quietHoursEnd), nowInZone);
    }

    /**
     * End instant (UTC) of the daily window [start, end) containing the wall time of
     * {@code nowInZone}, or empty when the window is absent/inverted or does not contain now.
     */
    private static Optional<LocalDateTime> windowEndUtc(LocalTime start, LocalTime end, ZonedDateTime nowInZone) {
        if (start == null || end == null || start.equals(end)) return Optional.empty();
        LocalTime t = nowInZone.toLocalTime();
        if (start.isBefore(end)) {
            // Same-day window: quiet for t in [start, end).
            if (t.isBefore(start) || !t.isBefore(end)) return Optional.empty();
            return Optional.of(endAtUtc(nowInZone, end, 0));
        }
        // Window wraps midnight: quiet for t >= start (ends tomorrow) or t < end (ends today).
        if (!t.isBefore(start)) return Optional.of(endAtUtc(nowInZone, end, 1));
        if (t.isBefore(end)) return Optional.of(endAtUtc(nowInZone, end, 0));
        return Optional.empty();
    }

    private static LocalDateTime endAtUtc(ZonedDateTime nowInZone, LocalTime end, int plusDays) {
        ZonedDateTime endZoned = nowInZone.toLocalDate().plusDays(plusDays).atTime(end)
            .atZone(nowInZone.getZone());
        return LocalDateTime.ofInstant(endZoned.toInstant(), ZoneOffset.UTC);
    }

    private static LocalTime parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value.trim());
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
