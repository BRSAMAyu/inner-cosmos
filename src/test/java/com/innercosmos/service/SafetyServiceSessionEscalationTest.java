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
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);
    }

    @Test
    @DisplayName("3.9: repeated MEDIUM-tier matches within one session escalate to HIGH/block even though a single one stays MEDIUM")
    void repeatedMediumMatches_sameSession_escalateToHighBlock() {
        when(safetyBoundaryFilter.inspect(any()))
                .thenReturn(SafetyMatch.hit("OTHER", "MEDIUM", "other_rule", "FLAG"));

        SafetyResult firstTurn = safetyService.check("这次真的很难受", USER_ID, SESSION_ID);
        assertEquals("MEDIUM", firstTurn.riskLevel, "a single MEDIUM signal alone must stay MEDIUM (baseline unaffected)");
        assertFalse(firstTurn.blockModelCall);

        safetyService.check("还是觉得很难受，压力很大", USER_ID, SESSION_ID);
        SafetyResult thirdTurn = safetyService.check("真的撑不下去了", USER_ID, SESSION_ID);

        assertEquals("HIGH", thirdTurn.riskLevel, "the ACCUMULATED session pattern must escalate the effective risk level");
        assertTrue(thirdTurn.blockModelCall);
        assertEquals("SESSION_ESCALATION", thirdTurn.riskType);
        assertEquals("RESOURCE_PAGE", thirdTurn.handledAction);
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
}
