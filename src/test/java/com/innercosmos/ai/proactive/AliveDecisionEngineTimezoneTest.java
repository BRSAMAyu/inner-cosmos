package com.innercosmos.ai.proactive;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.PrivateTimerMapper;
import com.innercosmos.mapper.ProactiveEventLogMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.WakeIntentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AliveDecisionEngineTimezoneTest {
    @Mock LlmClient llm;
    @Mock QuietWindowResolver quiet;
    @Mock PrivateTimerMapper timers;
    @Mock WakeIntentService wakeIntents;
    @Mock ProactiveEventLogMapper events;
    @Mock ProactiveDeliveryChannel delivery;
    @Mock UserPortraitService portraits;
    @Mock AgentUserRelationshipService relationships;
    @Mock UserProfileMapper profiles;
    @InjectMocks AliveDecisionEngine engine;

    @Test
    void utcRuntimeSchedulesTheExactInstantUsingThePersistedUserZone() {
        long userId = 88001L;
        Instant now = Instant.parse("2026-01-15T12:30:00Z");
        engine.useClock(Clock.fixed(now, ZoneOffset.UTC));
        UserProfile profile = new UserProfile();
        profile.userId = userId;
        profile.timezone = "America/New_York";
        when(profiles.selectList(any(QueryWrapper.class))).thenReturn(List.of(profile));
        when(quiet.canPushNow(eq(userId), any(ZonedDateTime.class)))
            .thenReturn(new QuietWindowResolver.Reason(false, ""));
        when(events.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(events.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(portraits.getAll(userId)).thenReturn(null);
        when(relationships.getOrInit(userId)).thenReturn(null);
        when(llm.chat(any(LlmRequest.class))).thenReturn("""
            {"decide":"schedule","wait_minutes":30,"content_for_user":"半小时后见。","reason":"agreed"}
            """);

        engine.tick(userId);

        Instant preferred = now.plus(Duration.ofMinutes(30));
        verify(wakeIntents).scheduleAtInstants(eq(userId), anyString(), anyString(), eq("半小时后见。"),
            eq(preferred.minus(Duration.ofMinutes(5))), eq(preferred), eq(preferred.plus(Duration.ofHours(6))),
            eq("America/New_York"), eq("alive-decision"));
        verify(quiet).canPushNow(eq(userId), argThat(value ->
            value.getZone().getId().equals("America/New_York") && value.toInstant().equals(now)));
    }
}
