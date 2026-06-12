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
}