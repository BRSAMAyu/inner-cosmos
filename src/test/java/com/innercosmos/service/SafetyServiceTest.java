package com.innercosmos.service;

import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.exception.SafetyBlockedException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafetyServiceTest {

    @Mock
    private SafetyEventMapper safetyEventMapper;

    @Mock
    private SafetyBoundaryFilter safetyBoundaryFilter;

    @Mock
    private SafetyReviewService safetyReviewService;

    private SafetyServiceImpl safetyService;

    /** Reuses the real distress detector so the signal tier is exercised realistically. */
    private final DistressSignalDetector distressSignalDetector = new DistressSignalDetector();

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @BeforeEach
    void setUp() {
        // Gemini audit 3.9: a fresh aggregator per test (JUnit's default per-method test
        // instance lifecycle already gives each test its own SafetyServiceTest, so this alone
        // is enough to keep session state from leaking across test methods that reuse SESSION_ID).
        safetyService = new SafetyServiceImpl(safetyEventMapper, safetyBoundaryFilter,
                safetyReviewService, distressSignalDetector,
                new SessionRiskAggregator(java.time.Clock.systemUTC()), true);
    }

    // --- check (returns SafetyResult) ---

    @Test
    @DisplayName("check with null text returns LOW risk")
    void check_nullText_returnsLowRisk() {
        SafetyResult result = safetyService.check(null, USER_ID, SESSION_ID);

        assertEquals("LOW", result.riskLevel);
        assertEquals("NONE", result.riskType);
        assertFalse(result.blockModelCall);
    }

    @Test
    @DisplayName("check with blank text returns LOW risk without calling filter")
    void check_blankText_returnsLowRisk() {
        // Blank text is short-circuited before filter is called, so no stub needed
        SafetyResult result = safetyService.check("   ", USER_ID, SESSION_ID);

        assertEquals("LOW", result.riskLevel);
        assertEquals("NONE", result.riskType);
        assertFalse(result.blockModelCall);
    }

    @Test
    @DisplayName("check with safe text returns LOW risk")
    void check_safeText_returnsLowRisk() {
        when(safetyBoundaryFilter.inspect("Today is a nice day."))
                .thenReturn(SafetyMatch.safe());

        SafetyResult result = safetyService.check("Today is a nice day.", USER_ID, SESSION_ID);

        assertEquals("LOW", result.riskLevel);
        assertEquals("NONE", result.riskType);
        assertFalse(result.blockModelCall);
        assertNull(result.matchedRule);
    }

    @Test
    @DisplayName("check with crisis keyword returns HIGH risk and blocks")
    void check_crisisKeyword_returnsHighRisk() {
        // Build a string containing a crisis keyword using char codes
        // "zi sha" (suicide) = U+81EA U+6740
        String crisisText = "I want to " + String.valueOf(new char[]{0x81EA, 0x6740});
        when(safetyBoundaryFilter.inspect(crisisText))
                .thenReturn(SafetyMatch.hit("CRISIS_KEYWORD", "HIGH", "crisis_rule", "RESOURCE_PAGE"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyResult result = safetyService.check(crisisText, USER_ID, SESSION_ID);

        assertEquals("HIGH", result.riskLevel);
        assertEquals("CRISIS_KEYWORD", result.riskType);
        assertTrue(result.blockModelCall);
        assertNotNull(result.safeMessage);
        verify(safetyEventMapper).insert(any(SafetyEvent.class));
    }

    @Test
    @DisplayName("check with abuse keyword returns HIGH risk but does not block")
    void check_abuseKeyword_returnsHighRiskNoBlock() {
        // "wu ru" (insult) = U+4FAE U+8FB1
        String abuseText = "some abuse " + String.valueOf(new char[]{0x4FAE, 0x8FB1});
        when(safetyBoundaryFilter.inspect(abuseText))
                .thenReturn(SafetyMatch.hit("ABUSE", "HIGH", "abuse_rule", "FLAG"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyResult result = safetyService.check(abuseText, USER_ID, SESSION_ID);

        assertEquals("HIGH", result.riskLevel);
        assertEquals("ABUSE", result.riskType);
        assertFalse(result.blockModelCall);
        assertEquals("FLAG", result.handledAction);
        verify(safetyEventMapper).insert(any(SafetyEvent.class));
    }

    @Test
    @DisplayName("check with other matched rule returns MEDIUM risk")
    void check_otherMatch_returnsMediumRisk() {
        when(safetyBoundaryFilter.inspect("suspicious text"))
                .thenReturn(SafetyMatch.hit("OTHER", "MEDIUM", "other_rule", "FLAG"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyResult result = safetyService.check("suspicious text", USER_ID, SESSION_ID);

        assertEquals("MEDIUM", result.riskLevel);
        assertEquals("OTHER", result.riskType);
        assertFalse(result.blockModelCall);
        assertEquals("FLAG", result.handledAction);
    }

    // --- checkText (throws on block) ---

    @Test
    @DisplayName("checkText with safe text does not throw")
    void checkText_safeText_noThrow() {
        when(safetyBoundaryFilter.inspect("I feel happy today"))
                .thenReturn(SafetyMatch.safe());

        assertDoesNotThrow(() -> safetyService.checkText(USER_ID, SESSION_ID, "I feel happy today"));
    }

    @Test
    @DisplayName("checkText with crisis keyword throws SafetyBlockedException")
    void checkText_crisisKeyword_throwsSafetyBlockedException() {
        // "zi sha" (suicide) = U+81EA U+6740
        String crisisText = String.valueOf(new char[]{0x81EA, 0x6740});
        when(safetyBoundaryFilter.inspect(crisisText))
                .thenReturn(SafetyMatch.hit("CRISIS_KEYWORD", "HIGH", "crisis", "RESOURCE_PAGE"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        assertThrows(SafetyBlockedException.class,
                () -> safetyService.checkText(USER_ID, SESSION_ID, crisisText));
    }

    @Test
    @DisplayName("checkText with abuse keyword does not throw (flag only)")
    void checkText_abuseKeyword_doesNotThrow() {
        // "wu ru" (insult) = U+4FAE U+8FB1
        String abuseText = String.valueOf(new char[]{0x4FAE, 0x8FB1});
        when(safetyBoundaryFilter.inspect(abuseText))
                .thenReturn(SafetyMatch.hit("ABUSE", "HIGH", "abuse", "FLAG"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        assertDoesNotThrow(() -> safetyService.checkText(USER_ID, SESSION_ID, abuseText));
    }

    // --- resources ---

    @Test
    @DisplayName("resources returns non-empty list")
    void resources_returnsNonEmptyList() {
        List<String> result = safetyService.resources();

        assertFalse(result.isEmpty());
        assertTrue(result.size() >= 2);
    }

    @Test
    @DisplayName("resources returns list with meaningful content")
    void resources_returnsMeaningfulContent() {
        List<String> result = safetyService.resources();

        for (String resource : result) {
            assertFalse(resource.isEmpty(), "Each resource string should not be empty");
        }
    }

    @Test
    @DisplayName("resources includes at least one dialable crisis hotline number (M-002)")
    void resources_containsHotlineNumber() {
        // Safety-of-life contract: the crisis funnel must surface a real, dialable number,
        // and it must match the tel-link regex in safety-harbor.html so it renders clickable.
        List<String> result = safetyService.resources();
        boolean hasPhone = result.stream().anyMatch(r ->
                r.matches(".*\\d{3,4}[-\\s]?\\d{7,8}.*")              // e.g. 010-82951332
                        || r.matches(".*\\d{3}[-\\s]?\\d{3}[-\\s]?\\d{4}.*")  // e.g. 400-161-9995
                        || r.matches(".*\\b(?:110|120|119|12320|12355|988)\\b.*")); // short codes
        assertTrue(hasPhone, "resources() must include at least one dialable crisis hotline number");
    }

    // --- safety event recording ---

    @Test
    @DisplayName("Safety event is recorded when crisis keyword matched")
    void safetyEventRecorded_onCrisisMatch() {
        String text = "dangerous";
        when(safetyBoundaryFilter.inspect(text))
                .thenReturn(SafetyMatch.hit("CRISIS_KEYWORD", "HIGH", "test_rule", "RESOURCE_PAGE"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        safetyService.check(text, USER_ID, SESSION_ID);

        verify(safetyEventMapper, times(1)).insert(any(SafetyEvent.class));
    }

    @Test
    @DisplayName("No safety event recorded for safe text")
    void noSafetyEvent_onSafeText() {
        when(safetyBoundaryFilter.inspect("safe text"))
                .thenReturn(SafetyMatch.safe());

        safetyService.check("safe text", USER_ID, SESSION_ID);

        verify(safetyEventMapper, never()).insert(any(SafetyEvent.class));
    }

    // --- VS-002: distress signal + synchronous semantic re-check ---

    @Test
    @DisplayName("distress signal + LLM says HIGH (genuine crisis) → blocks + resource page (regression-safe)")
    void check_distressGenuineCrisis_blocks() {
        // "累赘" carries a distress signal but no explicit crisis keyword in inspect().
        String text = "我真的是大家的累赘，想要彻底解脱";
        when(safetyBoundaryFilter.inspect(text)).thenReturn(SafetyMatch.safe());
        when(safetyReviewService.recheckSync(eq(USER_ID), eq(text), any(SafetyMatch.class)))
                .thenReturn(SafetyMatch.hitWithLlmReview("CRISIS_KEYWORD", "HIGH",
                        "DISTRESS_SIGNAL + LLM_REVIEW", "RESOURCE_PAGE", "genuine crisis"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyResult result = safetyService.check(text, USER_ID, SESSION_ID);

        assertEquals("HIGH", result.riskLevel);
        assertTrue(result.blockModelCall);
        assertEquals("RESOURCE_PAGE", result.handledAction);
        assertNotNull(result.safeMessage);
        verify(safetyReviewService).recheckSync(eq(USER_ID), eq(text), any(SafetyMatch.class));
    }

    @Test
    @DisplayName("distress signal + LLM says LOW (casual venting '今天加班累死了') → ALLOW, no block, no medicalizing")
    void check_distressCasualVenting_allows() {
        // Casual tired/overworked hyperbole carries NO distress signal, so the re-check
        // is never even invoked — this is the strongest false-positive guard: ordinary
        // venting never reaches the crisis judgment path. Must NOT be blocked or medicalized.
        String text = "今天加班累死了，困得要死";
        when(safetyBoundaryFilter.inspect(text)).thenReturn(SafetyMatch.safe());

        SafetyResult result = safetyService.check(text, USER_ID, SESSION_ID);

        assertEquals("LOW", result.riskLevel);
        assertFalse(result.blockModelCall);
        verifyNoInteractions(safetyReviewService);
        verify(safetyEventMapper, never()).insert(any(SafetyEvent.class));
    }

    @Test
    @DisplayName("distress signal + LLM says MEDIUM (non-crisis distress) → ALLOW as MEDIUM, no block")
    void check_distressMedium_allows() {
        String text = "最近撑不住了，压力好大";
        when(safetyBoundaryFilter.inspect(text)).thenReturn(SafetyMatch.safe());
        when(safetyReviewService.recheckSync(eq(USER_ID), eq(text), any(SafetyMatch.class)))
                .thenReturn(SafetyMatch.hitWithLlmReview("DISTRESS", "MEDIUM",
                        "DISTRESS_SIGNAL + LLM_CLEARED", null, "non-crisis distress"));

        SafetyResult result = safetyService.check(text, USER_ID, SESSION_ID);

        assertEquals("MEDIUM", result.riskLevel);
        assertFalse(result.blockModelCall);
    }

    @Test
    @DisplayName("semantic-recheck disabled → distress flows as LOW (current behavior), recheck never invoked")
    void check_distressDisabled_flowsLow() {
        SafetyServiceImpl disabled = new SafetyServiceImpl(safetyEventMapper, safetyBoundaryFilter,
                safetyReviewService, distressSignalDetector,
                new SessionRiskAggregator(java.time.Clock.systemUTC()), false);
        String text = "我真的是大家的累赘，想要彻底解脱";
        when(safetyBoundaryFilter.inspect(text)).thenReturn(SafetyMatch.safe());

        SafetyResult result = disabled.check(text, USER_ID, SESSION_ID);

        assertEquals("LOW", result.riskLevel);
        assertFalse(result.blockModelCall);
        verifyNoInteractions(safetyReviewService);
        verify(safetyEventMapper, never()).insert(any(SafetyEvent.class));
    }

    @Test
    @DisplayName("text without distress signal and without keyword → LOW, recheck never invoked")
    void check_noDistressNoKeyword_low() {
        String text = "I feel happy today";
        when(safetyBoundaryFilter.inspect(text)).thenReturn(SafetyMatch.safe());

        SafetyResult result = safetyService.check(text, USER_ID, SESSION_ID);

        assertEquals("LOW", result.riskLevel);
        verifyNoInteractions(safetyReviewService);
    }

    // --- VS-002-repair (RT-002) binding cases ---

    @Test
    @DisplayName("F9: recheckSync failure on crisis-bearing distress → HIGH block (never silently allow)")
    void check_recheckFailure_crisisBearing_blocksHigh_f9() {
        // Crisis-bearing distress text reaches recheckSync; on forced exception the
        // conservative path must return HIGH (block), not a non-blocking MEDIUM.
        String text = "想要了断";
        when(safetyBoundaryFilter.inspect(text)).thenReturn(SafetyMatch.safe());
        when(safetyReviewService.recheckSync(eq(USER_ID), eq(text), any(SafetyMatch.class)))
                .thenReturn(SafetyMatch.hitWithLlmReview("DISTRESS", "HIGH",
                        "DISTRESS_SIGNAL + LLM_UNAVAILABLE", "RESOURCE_PAGE",
                        "Re-check unavailable; kept conservative"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyResult result = safetyService.check(text, USER_ID, SESSION_ID);

        assertEquals("HIGH", result.riskLevel);
        assertTrue(result.blockModelCall, "F9: crisis-bearing recheck failure must block");
        assertEquals("RESOURCE_PAGE", result.handledAction);
    }

    @Test
    @DisplayName("F1: casual token + genuine-crisis indicator → recheck HIGH → block")
    void check_casualTokenPlusGenuineCrisis_blocks_f1() {
        // A casual token (加班/累死了) co-occurring with genuine-crisis indicators must
        // still reach a HIGH block. The service consumes the recheck verdict; the
        // fallback ordering (genuine-crisis before casual) is exercised end-to-end via
        // SafetyControllerTest, and unit-level here we assert the verdict is honored.
        String text = "今天加班累死了，我真的是个累赘，如果我不在了大家会更好吧";
        when(safetyBoundaryFilter.inspect(text)).thenReturn(SafetyMatch.safe());
        when(safetyReviewService.recheckSync(eq(USER_ID), eq(text), any(SafetyMatch.class)))
                .thenReturn(SafetyMatch.hitWithLlmReview("CRISIS_KEYWORD", "HIGH",
                        "DISTRESS_SIGNAL + LLM_REVIEW", "RESOURCE_PAGE", "genuine crisis"));
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyResult result = safetyService.check(text, USER_ID, SESSION_ID);

        assertEquals("HIGH", result.riskLevel);
        assertTrue(result.blockModelCall, "F1: genuine crisis must block despite casual tokens");
    }
}
