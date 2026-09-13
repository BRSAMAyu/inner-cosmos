package com.innercosmos.scheduler;

import com.innercosmos.ai.proactive.ProactiveDeliveryChannel;
import com.innercosmos.ai.proactive.QuietWindowResolver;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.service.WakeIntentQuietHoursPolicy;
import com.innercosmos.service.WakeIntentService;
import com.innercosmos.service.WakeIntentRelevanceEvaluator;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.safety.SafetyBoundaryFilter;
import com.innercosmos.safety.SafetyMatch;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WakeIntentDeliveryJobTest {
    private final WakeIntentService intents = mock(WakeIntentService.class);
    private final QuietWindowResolver quiet = mock(QuietWindowResolver.class);
    private final WakeIntentQuietHoursPolicy quietHours = mock(WakeIntentQuietHoursPolicy.class);
    private final ProactiveDeliveryChannel live = mock(ProactiveDeliveryChannel.class);
    private final SafetyBoundaryFilter safety = mock(SafetyBoundaryFilter.class);
    private final UserProfileMapper profiles = mock(UserProfileMapper.class);
    private final WakeIntentRelevanceEvaluator relevance = mock(WakeIntentRelevanceEvaluator.class);
    private final WakeIntentDeliveryJob job = new WakeIntentDeliveryJob(intents, quiet, quietHours, live, safety, profiles, relevance);

    WakeIntentDeliveryJobTest() {
        when(safety.inspect(anyString())).thenReturn(SafetyMatch.safe());
        when(relevance.evaluate(any())).thenReturn(WakeIntentRelevanceEvaluator.Decision.keep());
        // The platform/user quiet-hours policy is closed by default (legacy behaviour).
        when(quietHours.evaluate(any(), any())).thenReturn(WakeIntentQuietHoursPolicy.Decision.open());
    }

    @Test
    void quietWindowDefersWithinLatestBoundary() {
        WakeIntent intent = claimed();
        intent.latestAt = LocalDateTime.now().plusHours(1);
        when(quiet.canPushNow(eq(intent.userId), any())).thenReturn(new QuietWindowResolver.Reason(true, "focus"));

        job.decide(intent);

        verify(intents).defer(eq(intent), any(), eq("boundary:focus"));
        verifyNoInteractions(live);
    }

    @Test
    void resolverBoundaryWithoutKnownWindowEndReprobesShortly() {
        WakeIntent intent = claimed();
        intent.latestAt = LocalDateTime.now().plusHours(1);
        when(quiet.canPushNow(eq(intent.userId), any())).thenReturn(new QuietWindowResolver.Reason(true, "sleep"));

        job.decide(intent);

        ArgumentCaptor<LocalDateTime> deferUntil = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(intents).defer(eq(intent), deferUntil.capture(), eq("boundary:sleep"));
        // A boundary with no computable end (sleep/todo/focus) must not guess a far horizon:
        // it re-probes in ~15 minutes (UTC, like every stored wake-intent timestamp) and keeps
        // the DEFERRED row visible meanwhile.
        LocalDateTime expected = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(15);
        org.assertj.core.api.Assertions.assertThat(deferUntil.getValue())
            .as("defer target should be ~15 minutes ahead")
            .isAfter(expected.minusMinutes(1))
            .isBefore(expected.plusMinutes(1));
        verifyNoInteractions(live);
    }

    @Test
    void platformQuietHoursDeferUntilWindowEndEvenWhenResolverStaysOpen() {
        WakeIntent intent = claimed();
        intent.latestAt = LocalDateTime.now().plusHours(8);
        when(quiet.canPushNow(eq(intent.userId), any())).thenReturn(new QuietWindowResolver.Reason(false, ""));
        LocalDateTime windowEndUtc = LocalDateTime.now().plusHours(6);
        when(quietHours.evaluate(eq(intent.userId), any())).thenReturn(
            new WakeIntentQuietHoursPolicy.Decision(true, "platform_quiet_hours", windowEndUtc));

        job.decide(intent);

        // CP-26: the platform do-not-disturb window applies even for users whose 4-layer
        // resolver found nothing, and the defer carries the exact window end on the row.
        verify(intents).defer(intent, windowEndUtc, "boundary:platform_quiet_hours");
        verifyNoInteractions(live);
    }

    @Test
    void quietWindowOutlastingLatestDeliversOnceAtTheBoundaryInsteadOfExpiring() {
        WakeIntent intent = claimed();
        intent.latestAt = LocalDateTime.now().plusMinutes(5);
        when(quiet.canPushNow(eq(intent.userId), any())).thenReturn(new QuietWindowResolver.Reason(true, "quiet_hours"));
        when(quietHours.evaluate(eq(intent.userId), any())).thenReturn(new WakeIntentQuietHoursPolicy.Decision(
            true, "user_quiet_hours", LocalDateTime.now().plusHours(9)));
        when(live.hasActiveEmitter(intent.userId)).thenReturn(false);

        job.decide(intent);

        verify(intents, never()).defer(any(), any(), any());
        verify(intents).finishWithNotification(intent, "CONVERT_TO_IN_APP", "latest_window_boundary",
            intent.reasonForUser, intent.content);
        verify(live, never()).push(anyLong(), anyString(), anyString());
    }

    @Test
    void offlineDeliveryBecomesDurableInAppReturn() {
        WakeIntent intent = claimed();
        when(quiet.canPushNow(eq(intent.userId), any())).thenReturn(new QuietWindowResolver.Reason(false, ""));
        when(live.hasActiveEmitter(intent.userId)).thenReturn(false);

        job.decide(intent);

        verify(intents).finishWithNotification(intent, "CONVERT_TO_IN_APP", "user_offline",
            intent.reasonForUser, intent.content);
        verify(live, never()).push(anyLong(), anyString(), anyString());
    }

    @Test
    void liveDeliveryIsAlsoPersistedBeforeFanout() {
        WakeIntent intent = claimed();
        when(quiet.canPushNow(eq(intent.userId), any())).thenReturn(new QuietWindowResolver.Reason(false, ""));
        when(live.hasActiveEmitter(intent.userId)).thenReturn(true);
        when(intents.finishWithNotification(intent, "SEND_AND_IN_APP", "live_and_durable",
            intent.reasonForUser, intent.content)).thenReturn(true);

        job.decide(intent);

        var order = inOrder(live, intents);
        order.verify(intents).finishWithNotification(intent, "SEND_AND_IN_APP", "live_and_durable",
            intent.reasonForUser, intent.content);
        order.verify(live).push(intent.userId, intent.content, "wake_intent");
    }

    @Test
    void currentRiskDropsTheReturnBeforeAnyDelivery() {
        WakeIntent intent = claimed();
        SafetyMatch risky = new SafetyMatch();
        risky.matched = true;
        risky.riskType = "CRISIS_KEYWORD";
        when(safety.inspect(intent.content)).thenReturn(risky);

        job.decide(intent);

        verify(intents).finish(intent, "DROP", "risk:CRISIS_KEYWORD");
        verifyNoInteractions(live);
    }

    @Test
    void autonomousReturnHonorsLatestOptOut() {
        WakeIntent intent = claimed();
        intent.payloadRef = "alive-decision";
        UserProfile profile = new UserProfile();
        profile.proactiveIntensity = "OFF";
        when(profiles.selectList(any())).thenReturn(java.util.List.of(profile));

        job.decide(intent);

        verify(intents).finish(intent, "DROP", "user_proactive_preference_off");
        verifyNoInteractions(live);
    }

    @Test
    void resolvedContextIsDroppedBeforeDelivery() {
        WakeIntent intent = claimed();
        when(relevance.evaluate(intent)).thenReturn(
            new WakeIntentRelevanceEvaluator.Decision(false, "context_resolved_by_new_user_message"));

        job.decide(intent);

        verify(intents).finish(intent, "DROP", "context_resolved_by_new_user_message");
        verifyNoInteractions(live);
    }

    private WakeIntent claimed() {
        WakeIntent intent = new WakeIntent();
        intent.id = 91L;
        intent.userId = 92L;
        intent.reasonForUser = "Aurora 按约回来";
        intent.content = "我来赴约了。";
        intent.timezone = "Asia/Shanghai";
        intent.claimToken = "token";
        intent.latestAt = LocalDateTime.now().plusMinutes(5);
        return intent;
    }
}
