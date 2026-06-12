package com.innercosmos.ai.strategy;

import com.innercosmos.ai.mode.ActionSplitStrategy;
import com.innercosmos.ai.mode.DailyTalkStrategy;
import com.innercosmos.ai.mode.ModeRegistry;
import com.innercosmos.ai.mode.ModeStrategy;
import com.innercosmos.ai.mode.RelationReviewStrategy;
import com.innercosmos.ai.mode.SleepReviewStrategy;
import com.innercosmos.ai.mode.SocraticStrategy;
import com.innercosmos.ai.mode.ThoughtClarifyStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeStrategyTest {

    // --- name() for each strategy ---

    @Test
    void dailyTalkName() {
        assertEquals("DAILY_TALK", new DailyTalkStrategy().name());
    }

    @Test
    void socraticName() {
        assertEquals("SOCRATIC", new SocraticStrategy().name());
    }

    @Test
    void thoughtClarifyName() {
        assertEquals("THOUGHT_CLARIFY", new ThoughtClarifyStrategy().name());
    }

    @Test
    void actionSplitName() {
        assertEquals("ACTION_SPLIT", new ActionSplitStrategy().name());
    }

    @Test
    void relationReviewName() {
        assertEquals("RELATION_REVIEW", new RelationReviewStrategy().name());
    }

    @Test
    void sleepReviewName() {
        assertEquals("SLEEP_REVIEW", new SleepReviewStrategy().name());
    }

    // --- segment() is non-null and non-empty ---

    @Test
    void dailyTalkSegmentIsValid() {
        ModeStrategy strategy = new DailyTalkStrategy();
        String segment = strategy.segment();
        assertNotNull(segment);
        assertFalse(segment.isEmpty());
        assertTrue(segment.contains("[Mode: Daily Talk]"));
    }

    @Test
    void socraticSegmentIsValid() {
        ModeStrategy strategy = new SocraticStrategy();
        String segment = strategy.segment();
        assertNotNull(segment);
        assertFalse(segment.isEmpty());
        assertTrue(segment.contains("[Mode: Socratic Questioning]"));
    }

    @Test
    void thoughtClarifySegmentIsValid() {
        ModeStrategy strategy = new ThoughtClarifyStrategy();
        String segment = strategy.segment();
        assertNotNull(segment);
        assertFalse(segment.isEmpty());
        assertTrue(segment.contains("[Mode: Thought Clarify]"));
        assertTrue(segment.contains("[Fact]"));
        assertTrue(segment.contains("[Feeling]"));
        assertTrue(segment.contains("[Worry]"));
        assertTrue(segment.contains("[Need]"));
        assertTrue(segment.contains("[Next Step]"));
    }

    @Test
    void actionSplitSegmentIsValid() {
        ModeStrategy strategy = new ActionSplitStrategy();
        String segment = strategy.segment();
        assertNotNull(segment);
        assertFalse(segment.isEmpty());
        assertTrue(segment.contains("[Mode: Action Split]"));
    }

    @Test
    void relationReviewSegmentIsValid() {
        ModeStrategy strategy = new RelationReviewStrategy();
        String segment = strategy.segment();
        assertNotNull(segment);
        assertFalse(segment.isEmpty());
        assertTrue(segment.contains("[Mode: Relation Review]"));
    }

    @Test
    void sleepReviewSegmentIsValid() {
        ModeStrategy strategy = new SleepReviewStrategy();
        String segment = strategy.segment();
        assertNotNull(segment);
        assertFalse(segment.isEmpty());
        assertTrue(segment.contains("[Mode: Sleep Review]"));
    }

    // --- temperature() returns expected values ---

    @Test
    void dailyTalkTemperature() {
        assertEquals(0.85, new DailyTalkStrategy().temperature(), 0.0001);
    }

    @Test
    void socraticTemperature() {
        assertEquals(0.65, new SocraticStrategy().temperature(), 0.0001);
    }

    @Test
    void thoughtClarifyTemperature() {
        assertEquals(0.55, new ThoughtClarifyStrategy().temperature(), 0.0001);
    }

    @Test
    void actionSplitTemperature() {
        assertEquals(0.7, new ActionSplitStrategy().temperature(), 0.0001);
    }

    @Test
    void relationReviewTemperature() {
        assertEquals(0.7, new RelationReviewStrategy().temperature(), 0.0001);
    }

    @Test
    void sleepReviewTemperature() {
        assertEquals(0.6, new SleepReviewStrategy().temperature(), 0.0001);
    }

    // --- temperature is within reasonable bounds ---

    @Test
    void allTemperaturesInRange() {
        List<ModeStrategy> all = List.of(
            new DailyTalkStrategy(),
            new SocraticStrategy(),
            new ThoughtClarifyStrategy(),
            new ActionSplitStrategy(),
            new RelationReviewStrategy(),
            new SleepReviewStrategy()
        );
        for (ModeStrategy s : all) {
            assertTrue(s.temperature() >= 0.0 && s.temperature() <= 1.0,
                "Temperature out of range for " + s.name() + ": " + s.temperature());
        }
    }

    // --- requiresMultiTurnAcknowledgement() ---

    @Test
    void dailyTalkDoesNotRequireAcknowledgement() {
        assertFalse(new DailyTalkStrategy().requiresMultiTurnAcknowledgement());
    }

    @Test
    void socraticRequiresAcknowledgement() {
        assertTrue(new SocraticStrategy().requiresMultiTurnAcknowledgement());
    }

    @Test
    void thoughtClarifyRequiresAcknowledgement() {
        assertTrue(new ThoughtClarifyStrategy().requiresMultiTurnAcknowledgement());
    }

    @Test
    void actionSplitDoesNotRequireAcknowledgement() {
        assertFalse(new ActionSplitStrategy().requiresMultiTurnAcknowledgement());
    }

    @Test
    void relationReviewDoesNotRequireAcknowledgement() {
        assertFalse(new RelationReviewStrategy().requiresMultiTurnAcknowledgement());
    }

    @Test
    void sleepReviewDoesNotRequireAcknowledgement() {
        assertFalse(new SleepReviewStrategy().requiresMultiTurnAcknowledgement());
    }

    // --- ModeRegistry lookup ---

    @Test
    void modeRegistryReturnsCorrectStrategy() {
        ModeRegistry registry = new ModeRegistry(List.of(
            new DailyTalkStrategy(),
            new SocraticStrategy(),
            new ThoughtClarifyStrategy(),
            new ActionSplitStrategy(),
            new RelationReviewStrategy(),
            new SleepReviewStrategy()
        ));

        assertNotNull(registry.get("DAILY_TALK"));
        assertNotNull(registry.get("SOCRATIC"));
        assertNotNull(registry.get("THOUGHT_CLARIFY"));
        assertNotNull(registry.get("ACTION_SPLIT"));
        assertNotNull(registry.get("RELATION_REVIEW"));
        assertNotNull(registry.get("SLEEP_REVIEW"));
    }

    @Test
    void modeRegistryReturnsCorrectNameForLookedUpStrategy() {
        ModeRegistry registry = new ModeRegistry(List.of(
            new DailyTalkStrategy(),
            new SocraticStrategy()
        ));

        ModeStrategy found = registry.get("DAILY_TALK");
        assertEquals("DAILY_TALK", found.name());
    }

    @Test
    void modeRegistryReturnsNullForUnknownMode() {
        ModeRegistry registry = new ModeRegistry(List.of(new DailyTalkStrategy()));
        assertNull(registry.get("UNKNOWN_MODE"));
    }

    @Test
    void modeRegistryReturnsNullForNullMode() {
        ModeRegistry registry = new ModeRegistry(List.of(new DailyTalkStrategy()));
        assertNull(registry.get(null));
    }

    @Test
    void modeRegistryReturnsNullForEmptyMode() {
        ModeRegistry registry = new ModeRegistry(List.of(new DailyTalkStrategy()));
        assertNull(registry.get(""));
    }

    @Test
    void modeRegistryNamesContainsAllRegisteredModes() {
        ModeRegistry registry = new ModeRegistry(List.of(
            new DailyTalkStrategy(),
            new SocraticStrategy(),
            new ThoughtClarifyStrategy()
        ));
        List<String> names = registry.names();

        assertTrue(names.contains("DAILY_TALK"));
        assertTrue(names.contains("SOCRATIC"));
        assertTrue(names.contains("THOUGHT_CLARIFY"));
    }

    @Test
    void modeRegistryNamesIsSorted() {
        ModeRegistry registry = new ModeRegistry(List.of(
            new SleepReviewStrategy(),
            new DailyTalkStrategy(),
            new ActionSplitStrategy()
        ));
        List<String> names = registry.names();

        for (int i = 0; i < names.size() - 1; i++) {
            assertTrue(names.get(i).compareTo(names.get(i + 1)) <= 0,
                "Names should be sorted: " + names);
        }
    }

    @Test
    void modeRegistryEmptyListReturnsEmptyNames() {
        ModeRegistry registry = new ModeRegistry(List.of());
        assertTrue(registry.names().isEmpty());
        assertNull(registry.get("DAILY_TALK"));
    }
}