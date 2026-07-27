package com.innercosmos.service;

import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.mapper.SafetyEventMapper;
import com.innercosmos.safety.DistressSignalDetector;
import com.innercosmos.safety.SafetyBoundaryFilter;
import com.innercosmos.safety.SafetyMatch;
import com.innercosmos.safety.SafetyReviewService;
import com.innercosmos.safety.SessionRiskAggregator;
import com.innercosmos.service.impl.SafetyServiceImpl;
import com.innercosmos.vo.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

/**
 * Gemini audit 3.9 (CONFIRMED/P1): proves SafetyServiceImpl actually wires the session-scoped
 * escalation on top of its existing per-message MEDIUM/LOW matches (never replacing the explicit
 * CRISIS_KEYWORD/ABUSE HIGH paths, which are untouched and covered by the pre-existing
 * SafetyServiceTest), and that the resulting audit trail never contains the raw text that
 * triggered the escalation -- only the risk level/category.
 */
@ExtendWith(MockitoExtension.class)
class SafetyServiceSessionEscalationTest {

    @Mock private SafetyEventMapper safetyEventMapper;
    @Mock private SafetyBoundaryFilter safetyBoundaryFilter;
    @Mock private SafetyReviewService safetyReviewService;
    private final DistressSignalDetector distressSignalDetector = new DistressSignalDetector();

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 77L;

    private SafetyServiceImpl safetyService;

    @BeforeEach
    void setUp() {
        safetyService = new SafetyServiceImpl(safetyEventMapper, safetyBoundaryFilter,
                safetyReviewService, distressSignalDetector,
                new SessionRiskAggregator(Clock.systemUTC()), true);
        lenient().when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);
    }

    @Test
    @DisplayName("F3: repeated MEDIUM-tier matches become a gentle check-in without blocking Aurora")
    void repeatedMediumMatches_sameSession_becomeGentleCheckIn() {
        when(safetyBoundaryFilter.inspect(any()))
                .thenReturn(SafetyMatch.hit("OTHER", "MEDIUM", "other_rule", "FLAG"));

        SafetyResult firstTurn = safetyService.check("这次真的很难受", USER_ID, SESSION_ID);
        assertEquals("MEDIUM", firstTurn.riskLevel, "a single MEDIUM signal alone must stay MEDIUM (baseline unaffected)");
        assertFalse(firstTurn.blockModelCall);

        safetyService.check("还是觉得很难受，压力很大", USER_ID, SESSION_ID);
        SafetyResult thirdTurn = safetyService.check("真的撑不下去了", USER_ID, SESSION_ID);

        assertEquals("MEDIUM", thirdTurn.riskLevel);
        assertFalse(thirdTurn.blockModelCall, "ordinary repeated pain must not block Aurora");
        assertEquals("GENTLE_CHECK_IN", thirdTurn.riskType);
        assertEquals("SUPPORT_OFFER", thirdTurn.handledAction);
        assertEquals("GENTLE_CHECK_IN", thirdTurn.safetyState);
    }

    @Test
    @DisplayName("3.9: a DIFFERENT session for the same user does not accumulate risk from an unrelated session")
    void differentSession_doesNotShareAccumulatedRisk() {
        when(safetyBoundaryFilter.inspect(any()))
                .thenReturn(SafetyMatch.hit("OTHER", "MEDIUM", "other_rule", "FLAG"));

        safetyService.check("这次真的很难受", USER_ID, 501L);
        safetyService.check("还是觉得很难受", USER_ID, 501L);
        // A brand-new session (502L) starts its own accumulation from zero.
        SafetyResult otherSessionResult = safetyService.check("真的撑不下去了", USER_ID, 502L);

        assertEquals("MEDIUM", otherSessionResult.riskLevel,
                "a different session must not inherit another session's accumulated risk");
    }

    @Test
    @DisplayName("3.9: the escalation's own SafetyEvent audit row never contains the raw risk-triggering text -- category/level only")
    void escalationAuditRow_neverContainsRawText() throws Exception {
        when(safetyBoundaryFilter.inspect(any()))
                .thenReturn(SafetyMatch.hit("OTHER", "MEDIUM", "other_rule", "FLAG"));
        String sensitiveText1 = "这次真的很难受，具体原因是工作上的一次公开羞辱事件";
        String sensitiveText2 = "还是觉得很难受，压力很大，尤其是那次羞辱事件之后";
        String sensitiveText3 = "真的撑不下去了，那次羞辱事件让我彻底崩溃";

        safetyService.check(sensitiveText1, USER_ID, SESSION_ID);
        safetyService.check(sensitiveText2, USER_ID, SESSION_ID);
        safetyService.check(sensitiveText3, USER_ID, SESSION_ID);

        ArgumentCaptor<SafetyEvent> captor = ArgumentCaptor.forClass(SafetyEvent.class);
        org.mockito.Mockito.verify(safetyEventMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());

        for (SafetyEvent event : captor.getAllValues()) {
            for (Field field : SafetyEvent.class.getFields()) {
                Object value = field.get(event);
                if (value instanceof String stringValue) {
                    assertFalse(stringValue.contains("羞辱事件"),
                            "field '" + field.getName() + "' must never contain the raw risk-triggering text");
                }
            }
        }
    }

    @Test
    @DisplayName("foreground and stream reuse one safety decision for the same client message")
    void duplicateClientMessageId_reusesDecisionWithoutReinspectionOrRiskInflation() {
        when(safetyBoundaryFilter.inspect(any()))
                .thenReturn(SafetyMatch.hit("OTHER", "MEDIUM", "other_rule", "FLAG"));

        SafetyResult foreground = safetyService.check(
                "现在真的很难受", USER_ID, SESSION_ID, "turn-1", "zh-CN", "CN");
        SafetyResult stream = safetyService.check(
                "现在真的很难受", USER_ID, SESSION_ID, "turn-1", "zh-CN", "CN");
        SafetyResult secondUniqueTurn = safetyService.check(
                "还是很难受", USER_ID, SESSION_ID, "turn-2", "zh-CN", "CN");

        assertEquals("MEDIUM", foreground.riskLevel);
        assertEquals("MEDIUM", stream.riskLevel);
        assertEquals("MEDIUM", secondUniqueTurn.riskLevel,
                "two unique MEDIUM turns must not be inflated into three by foreground+stream");
        verify(safetyBoundaryFilter, times(2)).inspect(any());
    }

    @Test
    @DisplayName("twenty ordinary Chinese and English turns do not create a session escalation")
    void ordinaryChineseAndEnglish_twentyTurnsEach_neverEscalate() {
        when(safetyBoundaryFilter.inspect(any())).thenReturn(SafetyMatch.safe());

        for (int i = 0; i < 20; i++) {
            SafetyResult zh = safetyService.check(
                    "继续聊小说第 " + i + " 段", USER_ID, 701L, "zh-" + i, "zh-CN", "CN");
            SafetyResult en = safetyService.check(
                    "Continue with chapter " + i, USER_ID, 702L, "en-" + i, "en-SG", "SG");
            assertEquals("LOW", zh.riskLevel);
            assertEquals("LOW", en.riskLevel);
            assertFalse(zh.blockModelCall);
            assertFalse(en.blockModelCall);
        }
    }

    @Test
    @DisplayName("F5: one hundred concurrent copies share one safety computation and one durable event")
    void concurrentDuplicateClientMessage_sharesOneFuture() throws Exception {
        when(safetyBoundaryFilter.inspect(any()))
                .thenReturn(SafetyMatch.hit("OTHER", "MEDIUM", "other_rule", "FLAG"));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(16)) {
            java.util.List<java.util.concurrent.Callable<SafetyResult>> calls =
                    java.util.stream.IntStream.range(0, 100)
                            .mapToObj(i -> (java.util.concurrent.Callable<SafetyResult>) () ->
                                    safetyService.check("现在真的很难受", USER_ID, 900L,
                                            "same-turn", "en-SG", "SG"))
                            .toList();
            for (var future : executor.invokeAll(calls)) {
                assertEquals("MEDIUM", future.get().riskLevel);
            }
        }
        verify(safetyBoundaryFilter, times(1)).inspect(any());
        verify(safetyEventMapper, times(1)).insert(any(SafetyEvent.class));
    }

    @Test
    @DisplayName("F5: a failed shared computation is evicted and can be retried")
    void failedComputation_canRetry() {
        when(safetyBoundaryFilter.inspect(any()))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(SafetyMatch.safe());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                safetyService.check("hello", USER_ID, 901L, "retry-turn", "en-SG", "SG"));
        SafetyResult retried =
                safetyService.check("hello", USER_ID, 901L, "retry-turn", "en-SG", "SG");

        assertEquals("LOW", retried.riskLevel);
        verify(safetyBoundaryFilter, times(2)).inspect(any());
    }
}
