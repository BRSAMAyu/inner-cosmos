package com.innercosmos.scheduler;

import com.innercosmos.ai.proactive.ProactiveDeliveryChannel;
import com.innercosmos.ai.proactive.QuietWindowResolver;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.service.WakeIntentService;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.safety.SafetyBoundaryFilter;
import com.innercosmos.safety.SafetyMatch;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WakeIntentDeliveryJobTest {
    private final WakeIntentService intents = mock(WakeIntentService.class);
    private final QuietWindowResolver quiet = mock(QuietWindowResolver.class);
    private final ProactiveDeliveryChannel live = mock(ProactiveDeliveryChannel.class);
    private final SafetyBoundaryFilter safety = mock(SafetyBoundaryFilter.class);
    private final UserProfileMapper profiles = mock(UserProfileMapper.class);
    private final WakeIntentDeliveryJob job = new WakeIntentDeliveryJob(intents, quiet, live, safety, profiles);

    WakeIntentDeliveryJobTest() {
        when(safety.inspect(anyString())).thenReturn(SafetyMatch.safe());
    }

    @Test
    void quietWindowDelaysWithinLatestBoundary() {
        WakeIntent intent = claimed();
        intent.latestAt = LocalDateTime.now().plusHours(1);
        when(quiet.canPushNow(eq(intent.userId), any())).thenReturn(new QuietWindowResolver.Reason(true, "focus"));

        job.decide(intent);

        verify(intents).delay(eq(intent), any(), eq("boundary:focus"));
        verifyNoInteractions(live);
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
