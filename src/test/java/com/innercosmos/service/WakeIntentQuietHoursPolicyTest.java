package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** CP-26: quiet-hours window resolution must fail closed for privacy (default off = no change) yet defer precisely when enabled. */
class WakeIntentQuietHoursPolicyTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final UserProfileMapper profiles = Mockito.mock(UserProfileMapper.class);

    private static ZonedDateTime at(String isoWallTime) {
        return ZonedDateTime.parse(isoWallTime);
    }

    private static LocalDateTime utcEnd(ZonedDateTime nowInZone, LocalTime endWall, int plusDays) {
        return LocalDateTime.ofInstant(nowInZone.toLocalDate().plusDays(plusDays).atTime(endWall)
            .atZone(nowInZone.getZone()).toInstant(), ZoneOffset.UTC);
    }

    @Test
    void disabledPlatformWindowKeepsLegacyBehaviourEvenLateAtNight() {
        // Mirrors the application.yml default (enabled:false): no defer regardless of wall time.
        WakeIntentQuietHoursPolicy policy = new WakeIntentQuietHoursPolicy(false, "22:00", "07:00", profiles);
        assertThat(policy.evaluate(96101L, at("2026-09-14T23:30:00+08:00[Asia/Shanghai]")))
            .isEqualTo(WakeIntentQuietHoursPolicy.Decision.open());
    }

    @Test
    void applicationYmlShipsQuietHoursDisabledByDefault() {
        // Fail-closed config contract: the shipped default must exist and default to disabled,
        // so enabling do-not-disturb is an explicit operator choice, never a silent behaviour flip.
        assertThat(Files.exists(Path.of("src/main/resources/application.yml")))
            .as("test expects to run from the repository root").isTrue();
        String yml;
        try { yml = Files.readString(Path.of("src/main/resources/application.yml")); }
        catch (Exception failed) { throw new IllegalStateException(failed); }
        assertThat(yml).contains("quiet-hours:");
        assertThat(yml).contains("INNER_COSMOS_WAKE_QUIET_HOURS_ENABLED:false");
    }

    @Test
    void enabledPlatformWindowDefersUntilNextMorningAfterMidnightWrap() {
        WakeIntentQuietHoursPolicy policy = new WakeIntentQuietHoursPolicy(true, "22:00", "07:00", profiles);
        ZonedDateTime now = at("2026-09-14T23:30:00+08:00[Asia/Shanghai]");
        var decision = policy.evaluate(96102L, now);
        assertThat(decision.quiet()).isTrue();
        assertThat(decision.cause()).isEqualTo("platform_quiet_hours");
        assertThat(decision.deferUntilUtc()).isEqualTo(utcEnd(now, LocalTime.of(7, 0), 1));
    }

    @Test
    void enabledPlatformWindowEarlyMorningDefersUntilSameMorningEnd() {
        WakeIntentQuietHoursPolicy policy = new WakeIntentQuietHoursPolicy(true, "22:00", "07:00", profiles);
        ZonedDateTime now = at("2026-09-14T03:15:00+08:00[Asia/Shanghai]");
        var decision = policy.evaluate(96103L, now);
        assertThat(decision.quiet()).isTrue();
        assertThat(decision.deferUntilUtc()).isEqualTo(utcEnd(now, LocalTime.of(7, 0), 0));
    }

    @Test
    void daytimeIsOutsideTheWindow() {
        WakeIntentQuietHoursPolicy policy = new WakeIntentQuietHoursPolicy(true, "22:00", "07:00", profiles);
        assertThat(policy.evaluate(96104L, at("2026-09-14T12:00:00+08:00[Asia/Shanghai]")))
            .isEqualTo(WakeIntentQuietHoursPolicy.Decision.open());
    }

    @Test
    void userQuietHoursDeferEvenWhenThePlatformWindowIsDisabled() {
        // The user-configured do-not-disturb window is the primary surface; the platform
        // window is only a fallback default.
        UserProfile profile = new UserProfile();
        profile.userId = 96105L;
        profile.quietHoursStart = "21:00";
        profile.quietHoursEnd = "08:00";
        when(profiles.selectList(any(Wrapper.class))).thenReturn(List.of(profile));
        WakeIntentQuietHoursPolicy policy = new WakeIntentQuietHoursPolicy(false, "22:00", "07:00", profiles);
        ZonedDateTime now = at("2026-09-14T22:00:00+08:00[Asia/Shanghai]");
        var decision = policy.evaluate(96105L, now);
        assertThat(decision.quiet()).isTrue();
        assertThat(decision.cause()).isEqualTo("user_quiet_hours");
        assertThat(decision.deferUntilUtc()).isEqualTo(utcEnd(now, LocalTime.of(8, 0), 1));
    }

    @Test
    void overlappingWindowsDeferUntilTheLaterEnd() {
        UserProfile profile = new UserProfile();
        profile.userId = 96106L;
        profile.quietHoursStart = "21:00";
        profile.quietHoursEnd = "08:00";
        when(profiles.selectList(any(Wrapper.class))).thenReturn(List.of(profile));
        WakeIntentQuietHoursPolicy policy = new WakeIntentQuietHoursPolicy(true, "22:00", "07:00", profiles);
        ZonedDateTime now = at("2026-09-14T23:00:00+08:00[Asia/Shanghai]");
        var decision = policy.evaluate(96106L, now);
        assertThat(decision.quiet()).isTrue();
        assertThat(decision.deferUntilUtc()).isEqualTo(utcEnd(now, LocalTime.of(8, 0), 1));
    }

    @Test
    void malformedWindowsAreTreatedAsAbsentRatherThanGuessed() {
        WakeIntentQuietHoursPolicy policy = new WakeIntentQuietHoursPolicy(true, "not-a-time", "07:00", profiles);
        assertThat(policy.evaluate(96107L, at("2026-09-14T23:30:00+08:00[Asia/Shanghai]")))
            .isEqualTo(WakeIntentQuietHoursPolicy.Decision.open());
        assertThat(policy.evaluate(96107L, null)).isEqualTo(WakeIntentQuietHoursPolicy.Decision.open());
    }

    @Test
    void unknownUserWithoutProfileIsNotQuietedByAUserWindow() {
        when(profiles.selectList(any(Wrapper.class))).thenReturn(List.of());
        // Platform window disabled (the shipped default): a user without a configured
        // quiet-hours profile keeps the legacy always-deliverable behaviour.
        WakeIntentQuietHoursPolicy policy = new WakeIntentQuietHoursPolicy(false, "22:00", "07:00", profiles);
        assertThat(policy.evaluate(96108L, at("2026-09-14T03:00:00+08:00[Asia/Shanghai]")))
            .isEqualTo(WakeIntentQuietHoursPolicy.Decision.open());
    }
}
