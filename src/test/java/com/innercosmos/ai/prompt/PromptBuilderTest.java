package com.innercosmos.ai.prompt;

import com.innercosmos.ai.mode.DailyTalkStrategy;
import com.innercosmos.ai.mode.SocraticStrategy;
import com.innercosmos.vo.AuroraMemoryContextVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    // --- fluent API chaining ---

    @Test
    void fluentApiReturnsSameInstance() {
        PromptBuilder builder = new PromptBuilder();
        PromptBuilder ref1 = builder.withSystemBoundary();
        PromptBuilder ref2 = ref1.withConversationMode("DAILY_TALK");
        PromptBuilder ref3 = ref2.withUserProfile("profile");

        assertSame(builder, ref1);
        assertSame(builder, ref2);
        assertSame(builder, ref3);
    }

    @Test
    void allWithMethodsReturnSameInstance() {
        PromptBuilder builder = new PromptBuilder();
        AuroraMemoryContextVO ctx = new AuroraMemoryContextVO();

        assertSame(builder, builder.withSystemBoundary());
        assertSame(builder, builder.withConversationMode("DAILY_TALK"));
        assertSame(builder, builder.withModeSegment(new DailyTalkStrategy()));
        assertSame(builder, builder.withUserProfile("profile"));
        assertSame(builder, builder.withSummaryAnchor("anchor"));
        assertSame(builder, builder.withRecentMessages(List.of("msg")));
        assertSame(builder, builder.withGravityMemories(List.of("mem")));
        assertSame(builder, builder.withMemoryContext(ctx));
        assertSame(builder, builder.withRhythmAdvice("advice"));
        assertSame(builder, builder.withVoiceMetadata("meta"));
        assertSame(builder, builder.withUserInput("hello"));
        assertSame(builder, builder.withOutputSchema());
    }

    // --- build with no fields ---

    @Test
    void buildWithNoFieldsReturnsEmptyString() {
        PromptBuilder builder = new PromptBuilder();
        String result = builder.build();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildProducesNonNullResult() {
        PromptBuilder builder = new PromptBuilder();
        builder.withSystemBoundary();
        assertNotNull(builder.build());
    }

    // --- build with only system boundary ---

    @Test
    void buildWithOnlySystemBoundaryContainsAuroraIdentity() {
        PromptBuilder builder = new PromptBuilder();
        builder.withSystemBoundary();
        String result = builder.build();

        assertTrue(result.contains("Aurora"));
        assertTrue(result.contains("AI companion"));
        assertTrue(result.contains("Inner Cosmos"));
    }

    @Test
    void systemBoundaryIncludesSafetyRules() {
        PromptBuilder builder = new PromptBuilder();
        builder.withSystemBoundary();
        String result = builder.build();

        assertTrue(result.contains("No psychological diagnosis"));
        assertTrue(result.contains("self-harm"));
    }

    // --- conversation mode ---

    @Test
    void conversationModeIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withConversationMode("DAILY_TALK");
        String result = builder.build();

        assertTrue(result.contains("Current mode tag: DAILY_TALK"));
    }

    @Test
    void conversationModeBlankValueIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withConversationMode("");
        String result = builder.build();
        assertFalse(result.contains("Current mode tag"));
    }

    @Test
    void conversationModeNullValueIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withConversationMode(null);
        String result = builder.build();
        assertFalse(result.contains("Current mode tag"));
    }

    // --- mode segment ---

    @Test
    void modeSegmentIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withModeSegment(new DailyTalkStrategy());
        String result = builder.build();

        assertTrue(result.contains("[Mode: Daily Talk]"));
    }

    @Test
    void modeSegmentSetsTemperatureHint() {
        PromptBuilder builder = new PromptBuilder();
        builder.withModeSegment(new SocraticStrategy());
        String hint = builder.temperatureHint();

        assertTrue(hint.contains("0.65"));
    }

    @Test
    void modeSegmentNullStrategyIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withModeSegment(null);
        String result = builder.build();
        assertTrue(result.isEmpty());
    }

    @Test
    void temperatureHintEmptyWithoutModeSegment() {
        PromptBuilder builder = new PromptBuilder();
        assertEquals("", builder.temperatureHint());
    }

    // --- user profile ---

    @Test
    void userProfileIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withUserProfile("likes reading and coding");
        String result = builder.build();

        assertTrue(result.contains("likes reading and coding"));
    }

    @Test
    void userProfileBlankIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withUserProfile("   ");
        String result = builder.build();
        assertFalse(result.contains("user profile"));
    }

    // --- summary anchor ---

    @Test
    void summaryAnchorIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withSummaryAnchor("session anchor text");
        String result = builder.build();

        assertTrue(result.contains("session anchor text"));
    }

    // --- recent messages ---

    @Test
    void recentMessagesJoinedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withRecentMessages(List.of("Hello", "How are you?", "Good"));
        String result = builder.build();

        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("How are you?"));
        assertTrue(result.contains("Good"));
    }

    @Test
    void recentMessagesNullIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withRecentMessages(null);
        String result = builder.build();
        assertTrue(result.isEmpty());
    }

    @Test
    void recentMessagesEmptyListIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withRecentMessages(List.of());
        String result = builder.build();
        assertTrue(result.isEmpty());
    }

    // --- gravity memories ---

    @Test
    void gravityMemoriesIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withGravityMemories(List.of("memory1", "memory2"));
        String result = builder.build();

        assertTrue(result.contains("memory1"));
        assertTrue(result.contains("memory2"));
    }

    @Test
    void gravityMemoriesNullIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withGravityMemories(null);
        String result = builder.build();
        assertTrue(result.isEmpty());
    }

    // --- memory context (AuroraMemoryContextVO) ---

    @Test
    void memoryContextNullIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withMemoryContext(null);
        String result = builder.build();
        assertTrue(result.isEmpty());
    }

    @Test
    void memoryContextWithSessionSummaryAnchor() {
        AuroraMemoryContextVO ctx = new AuroraMemoryContextVO();
        ctx.sessionSummaryAnchor = "session anchor";
        PromptBuilder builder = new PromptBuilder();
        builder.withMemoryContext(ctx);
        String result = builder.build();

        assertTrue(result.contains("session anchor"));
    }

    @Test
    void memoryContextWithShortTermMessages() {
        AuroraMemoryContextVO ctx = new AuroraMemoryContextVO();
        ctx.shortTermMessages = List.of("msg1", "msg2");
        PromptBuilder builder = new PromptBuilder();
        builder.withMemoryContext(ctx);
        String result = builder.build();

        assertTrue(result.contains("msg1"));
        assertTrue(result.contains("msg2"));
    }

    @Test
    void memoryContextWithEmotionWeather() {
        AuroraMemoryContextVO ctx = new AuroraMemoryContextVO();
        ctx.emotionWeather = "sunny";
        PromptBuilder builder = new PromptBuilder();
        builder.withMemoryContext(ctx);
        String result = builder.build();

        assertTrue(result.contains("sunny"));
    }

    @Test
    void memoryContextWithLongTermMemoryNotes() {
        AuroraMemoryContextVO ctx = new AuroraMemoryContextVO();
        ctx.longTermMemoryNotes = List.of("note1", "note2");
        PromptBuilder builder = new PromptBuilder();
        builder.withMemoryContext(ctx);
        String result = builder.build();

        assertTrue(result.contains("note1"));
        assertTrue(result.contains("note2"));
    }

    @Test
    void memoryContextWithProactiveSuggestions() {
        AuroraMemoryContextVO ctx = new AuroraMemoryContextVO();
        ctx.proactiveSuggestions = List.of("suggestion1");
        PromptBuilder builder = new PromptBuilder();
        builder.withMemoryContext(ctx);
        String result = builder.build();

        assertTrue(result.contains("suggestion1"));
    }

    // --- rhythm advice ---

    @Test
    void rhythmAdviceIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withRhythmAdvice("slow down");
        String result = builder.build();

        assertTrue(result.contains("slow down"));
    }

    @Test
    void rhythmAdviceContinueIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withRhythmAdvice("CONTINUE");
        String result = builder.build();

        assertFalse(result.contains("CONTINUE"));
    }

    @Test
    void rhythmAdviceContinueCaseInsensitive() {
        PromptBuilder builder = new PromptBuilder();
        builder.withRhythmAdvice("continue");
        String result = builder.build();

        assertFalse(result.contains("continue"));
    }

    // --- voice metadata ---

    @Test
    void voiceMetadataIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withVoiceMetadata("whispery tone");
        String result = builder.build();

        assertTrue(result.contains("whispery tone"));
    }

    @Test
    void voiceMetadataBlankIgnored() {
        PromptBuilder builder = new PromptBuilder();
        builder.withVoiceMetadata("");
        String result = builder.build();
        assertFalse(result.contains("voice"));
    }

    // --- user input ---

    @Test
    void userInputIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withUserInput("I feel happy today");
        String result = builder.build();

        assertTrue(result.contains("I feel happy today"));
    }

    @Test
    void userInputNullPassesThrough() {
        PromptBuilder builder = new PromptBuilder();
        builder.withUserInput(null);
        String result = builder.build();
        // null input is not added (source: if (userInput != null))
        assertTrue(result.isEmpty());
    }

    // --- output schema ---

    @Test
    void outputSchemaIncludedInOutput() {
        PromptBuilder builder = new PromptBuilder();
        builder.withOutputSchema();
        String result = builder.build();

        assertTrue(result.contains("segments"));
        assertTrue(result.contains("speakCount"));
        assertTrue(result.contains("riskFlags"));
    }

    // --- full build ---

    @Test
    void buildWithAllFieldsAssemblesCompletePrompt() {
        AuroraMemoryContextVO ctx = new AuroraMemoryContextVO();
        ctx.sessionSummaryAnchor = "anchor-123";
        ctx.emotionWeather = "calm";

        String result = new PromptBuilder()
            .withSystemBoundary()
            .withConversationMode("DAILY_TALK")
            .withModeSegment(new DailyTalkStrategy())
            .withUserProfile("user profile data")
            .withSummaryAnchor("summary anchor")
            .withRecentMessages(List.of("recent msg"))
            .withGravityMemories(List.of("gravity mem"))
            .withMemoryContext(ctx)
            .withRhythmAdvice("take it easy")
            .withVoiceMetadata("morning voice")
            .withUserInput("Hello Aurora")
            .withOutputSchema()
            .build();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Aurora"));
        assertTrue(result.contains("DAILY_TALK"));
        assertTrue(result.contains("[Mode: Daily Talk]"));
        assertTrue(result.contains("user profile data"));
        assertTrue(result.contains("summary anchor"));
        assertTrue(result.contains("recent msg"));
        assertTrue(result.contains("gravity mem"));
        assertTrue(result.contains("anchor-123"));
        assertTrue(result.contains("calm"));
        assertTrue(result.contains("take it easy"));
        assertTrue(result.contains("morning voice"));
        assertTrue(result.contains("Hello Aurora"));
        assertTrue(result.contains("segments"));
    }

    // --- append behavior ---

    @Test
    void callingWithTwiceAppendsBoth() {
        PromptBuilder builder = new PromptBuilder();
        builder.withConversationMode("DAILY_TALK");
        builder.withConversationMode("SOCRATIC");
        String result = builder.build();

        // Each call adds a part, so both should be present
        assertTrue(result.contains("DAILY_TALK"));
        assertTrue(result.contains("SOCRATIC"));
    }
}