package com.innercosmos.safety;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafetyBoundaryFilterTest {

    @Mock
    private SafetyRule ruleA;

    @Mock
    private SafetyRule ruleB;

    @Mock
    private SafetyRule ruleC;

    private SafetyBoundaryFilter filter;

    @BeforeEach
    void setUp() {
        // Default mock returns null - per-case tests override behaviour
    }

    // inspect with no rules returns the safe match
    @Test
    void inspectWithNoRulesReturnsSafeMatch() {
        filter = new SafetyBoundaryFilter(List.of());

        SafetyMatch match = filter.inspect("anything at all");

        assertNotNull(match);
        assertFalse(match.matched);
    }

    // inspect with a matching rule returns that match
    @Test
    void inspectWithMatchingRuleReturnsMatch() {
        SafetyMatch hit = SafetyMatch.hit("crisis", "HIGH", "crisis-keyword", "RESPOND_WITH_CARE");
        when(ruleA.match("help me")).thenReturn(hit);

        filter = new SafetyBoundaryFilter(List.of(ruleA));

        SafetyMatch result = filter.inspect("help me");

        assertNotNull(result);
        assertTrue(result.matched);
        assertEquals("crisis", result.riskType);
        assertEquals("HIGH", result.riskLevel);
        assertEquals("crisis-keyword", result.matchedRule);
        assertEquals("RESPOND_WITH_CARE", result.handledAction);
    }

    // inspect with a non-matching rule returns the safe match
    @Test
    void inspectWithNonMatchingRuleReturnsSafe() {
        when(ruleA.match("hello world")).thenReturn(SafetyMatch.safe());

        filter = new SafetyBoundaryFilter(List.of(ruleA));

        SafetyMatch result = filter.inspect("hello world");

        assertNotNull(result);
        assertFalse(result.matched);
    }

    // inspect returns the first match when multiple rules chain in order
    @Test
    void inspectReturnsFirstMatchInOrder() {
        SafetyMatch firstHit = SafetyMatch.hit("crisis", "HIGH", "ruleA", "ACTION_A");

        when(ruleA.match("text")).thenReturn(firstHit);

        filter = new SafetyBoundaryFilter(List.of(ruleA, ruleB));

        SafetyMatch result = filter.inspect("text");

        assertTrue(result.matched);
        assertEquals("crisis", result.riskType);
        assertEquals("ruleA", result.matchedRule);
        // ruleB is never invoked because ruleA matched first
        verifyNoInteractions(ruleB);
    }

    // inspect stops iterating after the first match (short-circuits)
    @Test
    void inspectStopsAfterFirstMatch() {
        SafetyMatch firstHit = SafetyMatch.hit("crisis", "HIGH", "ruleA", "ACTION_A");
        when(ruleA.match("text")).thenReturn(firstHit);

        filter = new SafetyBoundaryFilter(List.of(ruleA, ruleB, ruleC));

        SafetyMatch result = filter.inspect("text");

        assertTrue(result.matched);
        assertEquals("ruleA", result.matchedRule);
        verify(ruleA).match("text");
        verifyNoInteractions(ruleB);
        verifyNoInteractions(ruleC);
    }

    // inspect with multiple non-matching rules returns safe and queries all
    @Test
    void inspectWithMultipleNonMatchingRulesReturnsSafe() {
        when(ruleA.match("hello")).thenReturn(SafetyMatch.safe());
        when(ruleB.match("hello")).thenReturn(SafetyMatch.safe());

        filter = new SafetyBoundaryFilter(List.of(ruleA, ruleB));

        SafetyMatch result = filter.inspect("hello");

        assertNotNull(result);
        assertFalse(result.matched);
        verify(ruleA).match("hello");
        verify(ruleB).match("hello");
    }

    // inspect chains rules in declared order when none match until the last
    @Test
    void inspectChainsRulesInDeclaredOrder() {
        SafetyMatch lateHit = SafetyMatch.hit("abuse", "MEDIUM", "ruleC", "WARN");
        when(ruleA.match("text")).thenReturn(SafetyMatch.safe());
        when(ruleB.match("text")).thenReturn(SafetyMatch.safe());
        when(ruleC.match("text")).thenReturn(lateHit);

        filter = new SafetyBoundaryFilter(List.of(ruleA, ruleB, ruleC));

        SafetyMatch result = filter.inspect("text");

        assertTrue(result.matched);
        assertEquals("ruleC", result.matchedRule);
        // Each earlier rule must have been tried
        verify(ruleA).match("text");
        verify(ruleB).match("text");
        verify(ruleC).match("text");
    }

    // inspect with null text still invokes rules (rules decide how to handle it)
    @Test
    void inspectWithNullTextInvokesRules() {
        when(ruleA.match(null)).thenReturn(SafetyMatch.safe());

        filter = new SafetyBoundaryFilter(List.of(ruleA));

        SafetyMatch result = filter.inspect(null);

        assertNotNull(result);
        assertFalse(result.matched);
        verify(ruleA).match(null);
    }

    // inspect verifies that well-behaved rules (returning SafetyMatch) work correctly
    @Test
    void inspectWithWellBehavedRulesReturnsSafe() {
        when(ruleA.match("text")).thenReturn(SafetyMatch.safe());
        filter = new SafetyBoundaryFilter(List.of(ruleA));
        SafetyMatch safeResult = filter.inspect("text");
        assertNotNull(safeResult);
        assertFalse(safeResult.matched);
    }

    // --- VS-002: DistressSignalDetector (signal tier, not a block) ---

    @Test
    void distressDetectorFiresOnImplicitBurdenSignal() {
        DistressSignalDetector detector = new DistressSignalDetector();

        assertTrue(detector.hasDistressSignal("我真的是大家的累赘"));
        assertTrue(detector.hasDistressSignal("如果我消失了大家会更好"));
        assertTrue(detector.hasDistressSignal("真的撑不下去了"));
        assertTrue(detector.hasDistressSignal("想要彻底解脱"));
        assertTrue(detector.hasDistressSignal("不想醒过来了"));
    }

    @Test
    void distressDetectorDoesNotFireOnCasualVenting() {
        DistressSignalDetector detector = new DistressSignalDetector();

        // Plain tired / overworked hyperbole — must NOT trip the distress tier.
        assertFalse(detector.hasDistressSignal("今天加班累死了，困得要死"));
        assertFalse(detector.hasDistressSignal("好开心的一天"));
        assertFalse(detector.hasDistressSignal("今天天气很好"));
    }

    @Test
    void distressDetectorHandlesNullAndBlank() {
        DistressSignalDetector detector = new DistressSignalDetector();

        assertFalse(detector.hasDistressSignal(null));
        assertFalse(detector.hasDistressSignal(""));
        assertFalse(detector.hasDistressSignal("   "));
    }

    // --- VS-002-repair (RT-002) F6: synchronous keyword list must not medicalize casual use ---

    @Test
    void crisisKeywordRule_doesNotBlockCasualVenting_f6() {
        CrisisKeywordRule rule = new CrisisKeywordRule();

        // F6: removed common vocabulary must no longer synchronously block (no match).
        assertFalse(rule.match("今天加班累死了，活着好累").matched);
        assertFalse(rule.match("这本书的结局让我绝望").matched);
        assertFalse(rule.match("会议终于结束了").matched);
        assertFalse(rule.match("失恋太痛苦了").matched);
        assertFalse(rule.match("我真的受够了").matched);
        // Borderline-casual phrases moved out of the synchronous block list.
        assertFalse(rule.match("想消失").matched);
        assertFalse(rule.match("撑不住了").matched);
        assertFalse(rule.match("熬不下去了").matched);
        assertFalse(rule.match("活着没意义").matched);
    }

    @Test
    void crisisKeywordRule_stillBlocksDirectCrisisTerms_regression() {
        CrisisKeywordRule rule = new CrisisKeywordRule();

        // Regression: near-always-crisis direct terms still synchronously block (HIGH).
        SafetyMatch m1 = rule.match("我想要自杀");
        assertTrue(m1.matched);
        assertEquals("HIGH", m1.riskLevel);
        assertTrue(rule.match("我想轻生").matched);
        assertTrue(rule.match("不想活了").matched);
        assertTrue(rule.match("希望消失").matched);
        assertTrue(rule.match("不想在了").matched);
        assertTrue(rule.match("生不如死").matched);
        assertTrue(rule.match("活不下去了").matched);
    }

    @Test
    void distressDetector_coversBorderlinePhrasesMovedFromKeywordList() {
        DistressSignalDetector detector = new DistressSignalDetector();

        // F6/F12: borderline-casual phrases removed from the synchronous block list must
        // still be visible to the semantic distress tier so they get a re-check (not a
        // silent synchronous block, and not a silent pass).
        assertTrue(detector.hasDistressSignal("想消失"));
        assertTrue(detector.hasDistressSignal("撑不住了"));
        assertTrue(detector.hasDistressSignal("熬不下去了"));
        assertTrue(detector.hasDistressSignal("活着没意义"));
    }

    // --- RT-002 reattack: broadened perception tier (signal → recheck, NOT a rigid block) ---

    @Test
    void distressDetector_firesOnBroadenedPerceptionSignals() {
        DistressSignalDetector detector = new DistressSignalDetector();

        // Fix #2: implicit-crisis phrasings that must reach Aurora's semantic recheck.
        // These TRIGGER a recheck; whether they block is then decided by the LLM/fallback.
        assertTrue(detector.hasDistressSignal("我是个负担"));
        assertTrue(detector.hasDistressSignal("今晚告别一切"));
        assertTrue(detector.hasDistressSignal("留下最后的话"));
        assertTrue(detector.hasDistressSignal("真的好想离开"));
    }
}