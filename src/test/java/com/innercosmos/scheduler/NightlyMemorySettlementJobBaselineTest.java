package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.GravityTimePolicy;
import com.innercosmos.service.MemorySettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IC-EMO-003: the nightly job recomputes the emotion baseline and bridges it into
 * the portrait once per user, inside the existing per-user try/catch loop.
 *
 * Updated for IC-DATA-001: user iteration now uses selectPage (paginated) instead
 * of selectList(null).
 */
@ExtendWith(MockitoExtension.class)
class NightlyMemorySettlementJobBaselineTest {

    @Mock private UserMapper userMapper;
    @Mock private MemoryCardMapper memoryCardMapper;
    @Mock private GravityService gravityService;
    @Mock private GravityTimePolicy gravityTimePolicy;
    @Mock private MemorySettlementService settlementService;
    @Mock private EmotionBaselineService emotionBaselineService;
    @Mock private EchoCapsuleMapper echoCapsuleMapper;

    private User user(long id) {
        User u = new User();
        u.id = id;
        return u;
    }

    /** Build a single-page result containing the given users (terminates loop). */
    private Page<User> singlePage(User... users) {
        Page<User> p = new Page<>();
        p.setRecords(List.of(users));
        return p;
    }

    @Test
    @DisplayName("nightlyRecalculation bridges the emotion baseline once per user")
    void bridgesBaselinePerUser() {
        // One page of 3 users — size < batchSizeForTest (1) so loop stops after page 1.
        Page<User> page = singlePage(user(1L), user(2L), user(3L));
        when(userMapper.selectPage(any(), isNull())).thenReturn(page);
        lenient().when(memoryCardMapper.selectList(any())).thenReturn(List.of());
        lenient().when(echoCapsuleMapper.findPublicByOwner(any())).thenReturn(List.<EchoCapsule>of());

        NightlyMemorySettlementJob job = new NightlyMemorySettlementJob(
                userMapper, memoryCardMapper, gravityService, gravityTimePolicy, settlementService,
                emotionBaselineService, echoCapsuleMapper);
        // Force batch size to match the page we return (3), so the termination condition
        // "batch.size() < batchSizeForTest" fires correctly (3 < 3 is false, so we need
        // the returned batch to be smaller than batchSizeForTest to stop).
        // Simplest: set batchSizeForTest=10 so 3 < 10 → stops after 1 page.
        job.batchSizeForTest = 10;

        job.nightlyRecalculation();

        verify(emotionBaselineService).bridgeToPortrait(1L);
        verify(emotionBaselineService).bridgeToPortrait(2L);
        verify(emotionBaselineService).bridgeToPortrait(3L);
        verify(emotionBaselineService, times(3)).bridgeToPortrait(any());
    }

    @Test
    @DisplayName("a baseline failure for one user does not abort the loop")
    void baselineFailureIsolated() {
        Page<User> page = singlePage(user(1L), user(2L));
        when(userMapper.selectPage(any(), isNull())).thenReturn(page);
        lenient().when(memoryCardMapper.selectList(any())).thenReturn(List.of());
        lenient().when(echoCapsuleMapper.findPublicByOwner(any())).thenReturn(List.<EchoCapsule>of());
        when(emotionBaselineService.bridgeToPortrait(1L)).thenThrow(new RuntimeException("boom"));

        NightlyMemorySettlementJob job = new NightlyMemorySettlementJob(
                userMapper, memoryCardMapper, gravityService, gravityTimePolicy, settlementService,
                emotionBaselineService, echoCapsuleMapper);
        job.batchSizeForTest = 10; // 2 users < 10 → single page, loop terminates

        job.nightlyRecalculation();

        // user 2 still processed despite user 1 throwing.
        verify(emotionBaselineService).bridgeToPortrait(2L);
    }
}
