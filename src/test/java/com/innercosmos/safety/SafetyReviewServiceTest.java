package com.innercosmos.safety;

import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.mapper.SafetyEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SafetyReviewService}, focused on the deterministic acute-crisis
 * floor that must hold across ALL verdict paths (live-LLM success, fallback, catch).
 */
@ExtendWith(MockitoExtension.class)
class SafetyReviewServiceTest {

    @Mock
    private StructuredAiService structuredAiService;

    @Mock
    private SafetyEventMapper safetyEventMapper;

    private SafetyReviewService service;

    private static final Long USER_ID = 7L;

    @BeforeEach
    void setUp() {
        service = new SafetyReviewService(structuredAiService, safetyEventMapper);
    }

    /**
     * RT-002 open_risk #1: a permissive LIVE-LLM verdict (LOW) on a genuine acute-crisis
     * text must still be forced to HIGH + block by the deterministic floor on the success
     * path — exactly as the fallback and catch paths already do. Genuine-crisis text here
     * carries NO explicit crisis keyword, so it relies entirely on the floor.
     */
    @Test
    @DisplayName("open_risk #1: mocked LOW live-LLM + genuine-crisis text → HIGH/block (success-path floor)")
    void recheck_liveLlmLow_butGenuineCrisis_forcedHigh_block() {
        // "我是累赘，拖累大家，想要解脱" — genuine acute crisis, no explicit keyword.
        String text = "我是累赘，拖累大家，想要解脱";

        // Live LLM returns an (incorrectly) permissive LOW verdict.
        SafetyReviewService.SafetyReviewResult low =
                new SafetyReviewService.SafetyReviewResult();
        low.riskLevel = "LOW";
        low.riskType = "DISTRESS";
        low.explanation = "not a crisis";
        low.requiresBlock = false;

        when(structuredAiService.call(eq(USER_ID), eq("SAFETY_REVIEW"), anyString(), any(Map.class),
                eq(SafetyReviewService.SafetyReviewResult.class), any())).thenReturn(low);
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyMatch result = service.recheckSync(USER_ID, text, SafetyMatch.safe());

        assertEquals("HIGH", result.riskLevel, "Floor must override the permissive LOW verdict");
        assertEquals("RESOURCE_PAGE", result.handledAction, "High must route to the resource page (block)");
    }

    /**
     * Symmetric guard on the success path: when the live LLM correctly clears a NON-crisis
     * text, the floor must NOT spuriously escalate it. Despair-without-intent stays LOW here
     * (LLM judgment governs; floor only fires on genuine acute-crisis indicators).
     */
    @Test
    @DisplayName("success-path floor does not escalate a correctly cleared non-crisis text")
    void recheck_liveLlmLow_nonCrisis_notEscalated() {
        // Mild hopelessness with NO acute-crisis indicator — floor must stay out of the way.
        String text = "最近觉得没什么意义，熬不下去";

        SafetyReviewService.SafetyReviewResult low =
                new SafetyReviewService.SafetyReviewResult();
        low.riskLevel = "LOW";
        low.riskType = "DISTRESS";
        low.explanation = "ordinary distress";
        low.requiresBlock = false;

        when(structuredAiService.call(eq(USER_ID), eq("SAFETY_REVIEW"), anyString(), any(Map.class),
                eq(SafetyReviewService.SafetyReviewResult.class), any())).thenReturn(low);
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyMatch result = service.recheckSync(USER_ID, text, SafetyMatch.safe());

        assertEquals("LOW", result.riskLevel, "Floor must not fire on non-acute distress");
    }

    /**
     * F-NEW-1 (despair case): despair-without-intent through the fallback resolves to a
     * non-blocking MEDIUM by design (vision §9 + user clarification 2026-06-16). Aurora
     * perceives and comforts; it does not medicalize ordinary hopelessness.
     */
    @Test
    @DisplayName("F-NEW-1: despair-without-intent → fallback MEDIUM (intended, non-blocking)")
    void recheck_despairWithoutIntent_fallbackMedium_intended() {
        // Force the fallback path by having the structured call invoke the fallback supplier.
        String text = "最近觉得好绝望，撑不下去，活着没意义";
        when(structuredAiService.call(eq(USER_ID), eq("SAFETY_REVIEW"), anyString(), any(Map.class),
                eq(SafetyReviewService.SafetyReviewResult.class), any()))
                .thenAnswer(invocation -> {
                    // Invoke the fallback supplier to exercise the fallback path deterministically.
                    var supplier = invocation.getArgument(5, java.util.function.Supplier.class);
                    return supplier.get();
                });
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyMatch result = service.recheckSync(USER_ID, text, SafetyMatch.safe());

        assertEquals("MEDIUM", result.riskLevel, "Despair-without-intent is intentionally non-blocking MEDIUM");
        assertTrue(result.handledAction == null || !"RESOURCE_PAGE".equals(result.handledAction),
                "MEDIUM despair must not route to the resource page (no block)");
    }

    /**
     * Regression: a clear-farewell acute indicator ("告别一切") on the fallback path must
     * produce HIGH/block (added to the floor per fix #3).
     */
    @Test
    @DisplayName("fix #3: 告别一切 + fallback → HIGH/block (clear-farewell acute floor)")
    void recheck_clearFarewell_fallbackHigh_block() {
        String text = "今晚是告别一切的时候了，留下最后的话";
        when(structuredAiService.call(eq(USER_ID), eq("SAFETY_REVIEW"), anyString(), any(Map.class),
                eq(SafetyReviewService.SafetyReviewResult.class), any()))
                .thenAnswer(invocation -> {
                    var supplier = invocation.getArgument(5, java.util.function.Supplier.class);
                    return supplier.get();
                });
        when(safetyEventMapper.insert(any(SafetyEvent.class))).thenReturn(1);

        SafetyMatch result = service.recheckSync(USER_ID, text, SafetyMatch.safe());

        assertEquals("HIGH", result.riskLevel, "Clear farewell must hit the acute-crisis floor");
        assertEquals("RESOURCE_PAGE", result.handledAction);
    }
}
