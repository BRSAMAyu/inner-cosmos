package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.MemorySettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * IC-DATA-001: NightlyMemorySettlementJob must iterate users in pages
 * rather than loading all users in a single selectList call.
 *
 * <p>The job exposes a package-private {@code batchSizeForTest} field so that
 * tests can force small batches without touching production constants.
 */
@ExtendWith(MockitoExtension.class)
class NightlyMemorySettlementJobPaginationTest {

    @Mock private UserMapper userMapper;
    @Mock private MemoryCardMapper memoryCardMapper;
    @Mock private GravityService gravityService;
    @Mock private MemorySettlementService settlementService;
    @Mock private EmotionBaselineService emotionBaselineService;
    @Mock private EchoCapsuleMapper echoCapsuleMapper;

    private NightlyMemorySettlementJob job;

    private User user(long id) {
        User u = new User();
        u.id = id;
        return u;
    }

    /** Build a Page result containing the given users. */
    @SuppressWarnings("unchecked")
    private IPage<User> pageOf(User... users) {
        Page<User> p = new Page<>();
        p.setRecords(List.of(users));
        return p;
    }

    @BeforeEach
    void setUp() {
        job = new NightlyMemorySettlementJob(
                userMapper, memoryCardMapper, gravityService,
                settlementService, emotionBaselineService, echoCapsuleMapper);
        // Force batch size = 2 so 5 users produce ceil(5/2) = 3 batches.
        job.batchSizeForTest = 2;

        // Capsule decay: return empty list for every user so we don't need
        // extra stubbing for the capsule mapper.
        lenient().when(echoCapsuleMapper.findPublicByOwner(any())).thenReturn(List.of());
        lenient().when(memoryCardMapper.selectList(any())).thenReturn(List.of());
    }

    // -----------------------------------------------------------------------
    // 1. selectPage is used — selectList(null) must NOT be called
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("IC-DATA-001: job uses selectPage not selectList for user iteration")
    void usesSelectPageNotSelectList() {
        // page 1 → users 1,2; page 2 → users 3,4; page 3 → user 5
        when(userMapper.selectPage(any(), isNull()))
                .thenReturn(pageOf(user(1L), user(2L)))
                .thenReturn(pageOf(user(3L), user(4L)))
                .thenReturn(pageOf(user(5L)));

        job.nightlyRecalculation();

        // selectList must never be called (old full-table-scan path).
        verify(userMapper, never()).selectList(any());
        // selectPage must be called at least once.
        verify(userMapper, atLeastOnce()).selectPage(any(), isNull());
    }

    // -----------------------------------------------------------------------
    // 2. All 5 users processed across the 3 batches
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("IC-DATA-001: all users processed — one settlementService call per user")
    void allUsersProcessed() {
        when(userMapper.selectPage(any(), isNull()))
                .thenReturn(pageOf(user(1L), user(2L)))
                .thenReturn(pageOf(user(3L), user(4L)))
                .thenReturn(pageOf(user(5L)));

        job.nightlyRecalculation();

        // Each user must receive exactly one updateThemeAggregation call.
        verify(settlementService).updateThemeAggregation(1L);
        verify(settlementService).updateThemeAggregation(2L);
        verify(settlementService).updateThemeAggregation(3L);
        verify(settlementService).updateThemeAggregation(4L);
        verify(settlementService).updateThemeAggregation(5L);
        verify(settlementService, times(5)).updateThemeAggregation(any());
    }

    // -----------------------------------------------------------------------
    // 3. Multiple batches fired (loop iterated 3 times for 5 users / batchSize 2)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("IC-DATA-001: loop fires 3 batches for 5 users with batchSize=2")
    void multipleBatchesFired() {
        when(userMapper.selectPage(any(), isNull()))
                .thenReturn(pageOf(user(1L), user(2L)))
                .thenReturn(pageOf(user(3L), user(4L)))
                .thenReturn(pageOf(user(5L)));

        job.nightlyRecalculation();

        // selectPage called exactly 3 times (pages 1, 2, 3).
        verify(userMapper, times(3)).selectPage(any(), isNull());
    }

    // -----------------------------------------------------------------------
    // 4. Loop terminates when an empty page is returned
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("IC-DATA-001: loop terminates cleanly when page returns no records")
    void loopTerminatesOnEmptyPage() {
        when(userMapper.selectPage(any(), isNull()))
                .thenReturn(pageOf(user(1L), user(2L)))
                .thenReturn(pageOf());   // empty → stop

        // Reset batch size to 2 (already set in setUp, but explicit for clarity)
        job.batchSizeForTest = 2;

        job.nightlyRecalculation();

        verify(userMapper, times(2)).selectPage(any(), isNull());
        verify(settlementService, times(2)).updateThemeAggregation(any());
    }
}
