package com.innercosmos.ai.perception;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TimeContextServiceTest {

    @Test
    void sameInstantIsGroundedInShanghaiSingaporeAndNewYork() {
        Instant instant = Instant.parse("2026-07-27T06:30:00Z");
        TimeContextService service = new TimeContextService(Clock.fixed(instant, ZoneOffset.UTC));

        var shanghai = service.now("Asia/Shanghai", null, "zh-CN", "下午",
                false, null, null);
        var singapore = service.now("Asia/Singapore", null, "en-SG", "afternoon",
                false, null, null);
        var newYork = service.now("America/New_York", null, "en-SG", "late night",
                false, null, null);

        assertThat(shanghai.localDateTime()).startsWith("2026-07-27T14:30:00+08:00");
        assertThat(shanghai.dateLabel()).contains("星期一");
        assertThat(shanghai.clientTimeHintStatus()).isEqualTo("MATCHED");
        assertThat(singapore.localDateTime()).startsWith("2026-07-27T14:30:00+08:00");
        assertThat(singapore.dateLabel()).contains("Mon, 27 Jul 2026 14:30");
        assertThat(newYork.localDateTime()).startsWith("2026-07-27T02:30:00-04:00");
        assertThat(newYork.isSleep()).isTrue();
    }

    @Test
    void newYorkOffsetFollowsDstAtTheSameLocalHour() {
        var summer = new TimeContextService(Clock.fixed(
                Instant.parse("2026-07-27T16:00:00Z"), ZoneOffset.UTC))
                .now("America/New_York", null, "en-SG", null,
                        false, null, null);
        var winter = new TimeContextService(Clock.fixed(
                Instant.parse("2026-12-27T17:00:00Z"), ZoneOffset.UTC))
                .now("America/New_York", null, "en-SG", null,
                        false, null, null);

        assertThat(summer.localDateTime()).contains("T12:00:00-04:00");
        assertThat(winter.localDateTime()).contains("T12:00:00-05:00");
    }

    @Test
    void validTurnTimezoneWinsAndInvalidTurnTimezoneFallsBackToProfile() {
        TimeContextService service = new TimeContextService(Clock.fixed(
                Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.now("America/New_York", "Asia/Shanghai", "en-SG", null,
                false, null, null).zoneId()).isEqualTo("America/New_York");
        assertThat(service.now("not-a-zone", "Asia/Singapore", "en-SG", null,
                false, null, null).zoneId()).isEqualTo("Asia/Singapore");
        assertThat(service.now("+08:00", "also-invalid", "zh-CN", null,
                false, null, null).zoneId()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void clientLabelCanOnlyReportMismatchAndCannotOverrideServerInstant() {
        TimeContextService service = new TimeContextService(Clock.fixed(
                Instant.parse("2026-07-27T06:30:00Z"), ZoneOffset.UTC));

        var context = service.now("Asia/Shanghai", null, "zh-CN", "深夜",
                false, null, null);

        assertThat(context.label()).isEqualTo("下午");
        assertThat(context.localDateTime()).startsWith("2026-07-27T14:30:00+08:00");
        assertThat(context.clientTimeHintStatus()).isEqualTo("MISMATCH_IGNORED");
    }

    @Test
    void elapsedInteractionIsLocalizedAndFutureLegacyTimestampIsLeftEmpty() {
        Instant instant = Instant.parse("2026-07-27T06:30:00Z");
        TimeContextService service = new TimeContextService(Clock.fixed(instant, ZoneOffset.UTC));

        assertThat(service.now("Asia/Shanghai", null, "zh-CN", null,
                false, null, instant.minusSeconds(75 * 60)).lastInteractionLabel())
                .isEqualTo("1小时前");
        assertThat(service.now("Asia/Singapore", null, "en-SG", null,
                false, null, instant.minusSeconds(15 * 60)).lastInteractionLabel())
                .isEqualTo("15 minutes ago");
        assertThat(service.now("Asia/Shanghai", null, "zh-CN", null,
                false, null, instant.plusSeconds(3600)).lastInteractionLabel())
                .isEmpty();
    }
}
