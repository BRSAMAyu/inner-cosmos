package com.innercosmos.service;

import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.mapper.SafetyEventMapper;
import com.innercosmos.safety.SafetyBoundaryFilter;
import com.innercosmos.safety.SafetyMatch;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafetyServiceTest {

    @Mock
    private SafetyEventMapper safetyEventMapper;

    @Mock
    private SafetyBoundaryFilter safetyBoundaryFilter;

    private SafetyServiceImpl safetyService;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @BeforeEach
    void setUp() {
        safetyService = new SafetyServiceImpl(safetyEventMapper, safetyBoundaryFilter);
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
}
