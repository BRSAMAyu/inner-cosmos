package com.innercosmos.scheduler;

import com.innercosmos.ai.proactive.AliveDecisionEngine;
import com.innercosmos.ai.proactive.ProactiveDeliveryChannel;
import com.innercosmos.ai.proactive.ProactiveEngine;
import com.innercosmos.entity.PrivateTimer;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.PrivateTimerMapper;
import com.innercosmos.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuroraProactiveJobTest {
    @Test
    void ticksTheAccountOwnerRatherThanTheProfileRowId() {
        ProactiveEngine regular = mock(ProactiveEngine.class);
        AliveDecisionEngine alive = mock(AliveDecisionEngine.class);
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        PrivateTimerMapper timers = mock(PrivateTimerMapper.class);
        UserProfile profile = new UserProfile();
        profile.id = 11L;
        profile.userId = 99L;
        profile.proactiveIntensity = "ALIVE";
        when(profiles.selectList(any())).thenReturn(List.of(profile));
        when(timers.selectList(any())).thenReturn(List.<PrivateTimer>of());

        AuroraProactiveJob job = new AuroraProactiveJob();
        ReflectionTestUtils.setField(job, "engine", regular);
        ReflectionTestUtils.setField(job, "aliveEngine", alive);
        ReflectionTestUtils.setField(job, "deliveryChannel", mock(ProactiveDeliveryChannel.class));
        ReflectionTestUtils.setField(job, "userMapper", profiles);
        ReflectionTestUtils.setField(job, "timerMapper", timers);

        job.run();

        verify(alive).tick(99L);
        verify(alive, never()).tick(11L);
        verifyNoInteractions(regular);
    }
}
