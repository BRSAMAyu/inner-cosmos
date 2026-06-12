package com.innercosmos.ai.strategy;

import com.innercosmos.ai.agent.AuroraAgent;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.agent.MemoryExtractAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentReplyStrategyTest {

    // --- AuroraCompanionStrategy ---

    @Mock
    private AuroraAgent auroraAgentMock;

    @Test
    void auroraCompanionStrategyCode() {
        AuroraCompanionStrategy strategy = new AuroraCompanionStrategy(auroraAgentMock);
        assertEquals("AURORA_COMPANION", strategy.strategyCode());
    }

    @Test
    void auroraCompanionReplyDelegatesToAuroraAgent() {
        when(auroraAgentMock.reply(any(), eq("hello"), any(), eq(""))).thenReturn("hi there");
        AuroraCompanionStrategy strategy = new AuroraCompanionStrategy(auroraAgentMock);

        String result = strategy.reply("hello");
        assertEquals("hi there", result);
        verify(auroraAgentMock).reply(any(), eq("hello"), any(), eq(""));
    }

    @Test
    void auroraCompanionReplyWithNullReturnsMockValue() {
        when(auroraAgentMock.reply(any(), isNull(), any(), eq(""))).thenReturn("handled null");
        AuroraCompanionStrategy strategy = new AuroraCompanionStrategy(auroraAgentMock);

        String result = strategy.reply(null);
        assertEquals("handled null", result);
    }

    @Test
    void auroraCompanionReplyWithEmptyInput() {
        when(auroraAgentMock.reply(any(), eq(""), any(), eq(""))).thenReturn("response");
        AuroraCompanionStrategy strategy = new AuroraCompanionStrategy(auroraAgentMock);

        String result = strategy.reply("");
        assertNotNull(result);
        assertEquals("response", result);
    }

    // --- ThoughtShredderStrategy ---

    @Mock
    private MemoryExtractAgent memoryExtractAgentMock;

    @Test
    void thoughtShredderStrategyCode() {
        ThoughtShredderStrategy strategy = new ThoughtShredderStrategy(memoryExtractAgentMock);
        assertEquals("THOUGHT_SHREDDER", strategy.strategyCode());
    }

    @Test
    void thoughtShredderReplyDelegatesToExtractAgent() {
        when(memoryExtractAgentMock.summarize("thoughts to shred")).thenReturn("summary text");
        ThoughtShredderStrategy strategy = new ThoughtShredderStrategy(memoryExtractAgentMock);

        String result = strategy.reply("thoughts to shred");
        assertEquals("summary text", result);
        verify(memoryExtractAgentMock).summarize("thoughts to shred");
    }

    @Test
    void thoughtShredderReplyWithBlankInputReturnsDefault() {
        when(memoryExtractAgentMock.summarize(anyString())).thenReturn("a quiet observation");
        ThoughtShredderStrategy strategy = new ThoughtShredderStrategy(memoryExtractAgentMock);

        String result = strategy.reply("");
        assertNotNull(result);
    }

    @Test
    void thoughtShredderReplyWithNullInput() {
        when(memoryExtractAgentMock.summarize(isNull())).thenReturn("default summary");
        ThoughtShredderStrategy strategy = new ThoughtShredderStrategy(memoryExtractAgentMock);

        String result = strategy.reply(null);
        assertEquals("default summary", result);
    }

    // --- CapsuleChatStrategy ---

    @Mock
    private CapsuleAgent capsuleAgentMock;

    @Test
    void capsuleChatStrategyCode() {
        CapsuleChatStrategy strategy = new CapsuleChatStrategy(capsuleAgentMock);
        assertEquals("CAPSULE_CHAT", strategy.strategyCode());
    }

    @Test
    void capsuleChatReplyReturnsLlmResponse() {
        CapsuleAgent.CapsuleConversationResponse response = new CapsuleAgent.CapsuleConversationResponse();
        response.reply = "capsule reply";

        when(capsuleAgentMock.converse(
            any(), any(), any(), eq(1), eq("hello"), any()
        )).thenReturn(response);

        CapsuleChatStrategy strategy = new CapsuleChatStrategy(capsuleAgentMock);
        String result = strategy.reply("hello");
        assertEquals("capsule reply", result);
    }

    @Test
    void capsuleChatReplyFallsBackWhenReplyNull() {
        CapsuleAgent.CapsuleConversationResponse response = new CapsuleAgent.CapsuleConversationResponse();
        response.reply = null;

        when(capsuleAgentMock.converse(
            any(), any(), any(), anyInt(), anyString(), any()
        )).thenReturn(response);

        CapsuleChatStrategy strategy = new CapsuleChatStrategy(capsuleAgentMock);
        String result = strategy.reply("hello");
        assertNotNull(result);
    }

    @Test
    void capsuleChatReplyWithNullInput() {
        CapsuleAgent.CapsuleConversationResponse response = new CapsuleAgent.CapsuleConversationResponse();
        response.reply = "default reply";

        when(capsuleAgentMock.converse(
            any(), any(), any(), anyInt(), any(), any()
        )).thenReturn(response);

        CapsuleChatStrategy strategy = new CapsuleChatStrategy(capsuleAgentMock);
        String result = strategy.reply(null);
        assertNotNull(result);
    }

    @Test
    void capsuleChatReplyWithEmptyInput() {
        CapsuleAgent.CapsuleConversationResponse response = new CapsuleAgent.CapsuleConversationResponse();
        response.reply = "heard nothing";

        when(capsuleAgentMock.converse(
            any(), any(), any(), anyInt(), anyString(), any()
        )).thenReturn(response);

        CapsuleChatStrategy strategy = new CapsuleChatStrategy(capsuleAgentMock);
        String result = strategy.reply("");
        assertNotNull(result);
    }

    // --- interface contract ---

    @Test
    void allStrategiesReturnNonNullStrategyCode() {
        AuroraCompanionStrategy aurora = new AuroraCompanionStrategy(auroraAgentMock);
        ThoughtShredderStrategy shredder = new ThoughtShredderStrategy(memoryExtractAgentMock);
        CapsuleChatStrategy capsule = new CapsuleChatStrategy(capsuleAgentMock);

        assertNotNull(aurora.strategyCode());
        assertNotNull(shredder.strategyCode());
        assertNotNull(capsule.strategyCode());
    }

    @Test
    void strategyCodesAreDistinct() {
        AuroraCompanionStrategy aurora = new AuroraCompanionStrategy(auroraAgentMock);
        ThoughtShredderStrategy shredder = new ThoughtShredderStrategy(memoryExtractAgentMock);
        CapsuleChatStrategy capsule = new CapsuleChatStrategy(capsuleAgentMock);

        assertNotEquals(aurora.strategyCode(), shredder.strategyCode());
        assertNotEquals(aurora.strategyCode(), capsule.strategyCode());
        assertNotEquals(shredder.strategyCode(), capsule.strategyCode());
    }

    @Test
    void allStrategiesImplementInterface() {
        assertTrue(new AuroraCompanionStrategy(auroraAgentMock) instanceof AgentReplyStrategy);
        assertTrue(new ThoughtShredderStrategy(memoryExtractAgentMock) instanceof AgentReplyStrategy);
        assertTrue(new CapsuleChatStrategy(capsuleAgentMock) instanceof AgentReplyStrategy);
    }

    @Test
    void strategyCodesMatchUpperSnakeCasePattern() {
        AuroraCompanionStrategy aurora = new AuroraCompanionStrategy(auroraAgentMock);
        ThoughtShredderStrategy shredder = new ThoughtShredderStrategy(memoryExtractAgentMock);
        CapsuleChatStrategy capsule = new CapsuleChatStrategy(capsuleAgentMock);

        assertTrue(aurora.strategyCode().matches("[A-Z_]+"));
        assertTrue(shredder.strategyCode().matches("[A-Z_]+"));
        assertTrue(capsule.strategyCode().matches("[A-Z_]+"));
    }

    @Test
    void allStrategyCodesAreUpperCase() {
        AuroraCompanionStrategy aurora = new AuroraCompanionStrategy(auroraAgentMock);
        ThoughtShredderStrategy shredder = new ThoughtShredderStrategy(memoryExtractAgentMock);
        CapsuleChatStrategy capsule = new CapsuleChatStrategy(capsuleAgentMock);

        assertEquals(aurora.strategyCode(), aurora.strategyCode().toUpperCase());
        assertEquals(shredder.strategyCode(), shredder.strategyCode().toUpperCase());
        assertEquals(capsule.strategyCode(), capsule.strategyCode().toUpperCase());
    }

    @Test
    void noStrategyCodeContainsSpacesOrDashes() {
        AuroraCompanionStrategy aurora = new AuroraCompanionStrategy(auroraAgentMock);
        ThoughtShredderStrategy shredder = new ThoughtShredderStrategy(memoryExtractAgentMock);
        CapsuleChatStrategy capsule = new CapsuleChatStrategy(capsuleAgentMock);

        assertFalse(aurora.strategyCode().contains(" "));
        assertFalse(aurora.strategyCode().contains("-"));
        assertFalse(shredder.strategyCode().contains(" "));
        assertFalse(shredder.strategyCode().contains("-"));
        assertFalse(capsule.strategyCode().contains(" "));
        assertFalse(capsule.strategyCode().contains("-"));
    }
}