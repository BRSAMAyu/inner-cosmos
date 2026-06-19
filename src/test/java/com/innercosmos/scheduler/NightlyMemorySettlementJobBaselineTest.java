package com.innercosmos.scheduler;

import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.MemorySettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IC-EMO-003: the nightly job recomputes the emotion baseline and bridges it into
 * the portrait once per user, inside the existing per-user try/catch loop.
 */
@ExtendWith(MockitoExtension.class)
class NightlyMemorySettlementJobBaselineTest {

    @Mock private UserMapper userMapper;
    @Mock private MemoryCardMapper memoryCardMapper;
    @Mock private GravityService gravityService;
    @Mock private MemorySettlementService settlementService;
    @Mock private EmotionBaselineService emotionBaselineService;
    @Mock private EchoCapsuleMapper echoCapsuleMapper;

    private User user(long id) {
        User u = new User();
        u.id = id;
        return u;
    }

    @Test
    @DisplayName("nightlyRecalculation bridges the emotion baseline once per user")
    void bridgesBaselinePerUser() {
        when(userMapper.selectList(any())).thenReturn(List.of(user(1L), user(2L), user(3L)));
        lenient().when(memoryCardMapper.selectList(any())).thenReturn(List.of());
        lenient().when(echoCapsuleMapper.selectList(any())).thenReturn(List.<EchoCapsule>of());

        NightlyMemorySettlementJob job = new NightlyMemorySettlementJob(
                userMapper, memoryCardMapper, gravityService, settlementService,
                emotionBaselineService, echoCapsuleMapper);

        job.nightlyRecalculation();

        verify(emotionBaselineService).bridgeToPortrait(1L);
        verify(emotionBaselineService).bridgeToPortrait(2L);
        verify(emotionBaselineService).bridgeToPortrait(3L);
        verify(emotionBaselineService, times(3)).bridgeToPortrait(any());
    }

    @Test
    @DisplayName("a baseline failure for one user does not abort the loop")
    void baselineFailureIsolated() {
        when(userMapper.selectList(any())).thenReturn(List.of(user(1L), user(2L)));
        lenient().when(memoryCardMapper.selectList(any())).thenReturn(List.of());
        lenient().when(echoCapsuleMapper.selectList(any())).thenReturn(List.<EchoCapsule>of());
        when(emotionBaselineService.bridgeToPortrait(1L)).thenThrow(new RuntimeException("boom"));

        NightlyMemorySettlementJob job = new NightlyMemorySettlementJob(
                userMapper, memoryCardMapper, gravityService, settlementService,
                emotionBaselineService, echoCapsuleMapper);

        job.nightlyRecalculation();

        // user 2 still processed despite user 1 throwing.
        verify(emotionBaselineService).bridgeToPortrait(2L);
    }
}
