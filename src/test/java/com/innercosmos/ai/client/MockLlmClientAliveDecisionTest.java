package com.innercosmos.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmClientAliveDecisionTest {
    private final MockLlmClient client = new MockLlmClient(Runnable::run);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aliveDecisionUsesStrictJsonAndMakesOneFirstContactWhenHistoryIsEmpty() throws Exception {
        String raw = client.chat(new LlmRequest(1L, "ALIVE_DECISION", "最近 7d 主动式日志: 无"));
        JsonNode decision = objectMapper.readTree(raw);

        assertEquals("push", decision.get("decide").asText());
        assertTrue(decision.get("content_for_user").asText().length() > 10);
        assertEquals("mock-first-contact-with-no-recent-proactive-history", decision.get("reason").asText());
    }

    @Test
    void aliveDecisionWaitsAfterRecentContactInsteadOfSpamming() throws Exception {
        String raw = client.chat(new LlmRequest(1L, "ALIVE_DECISION",
                "最近 7d 主动式日志: ALIVE_LLM:刚刚联系过"));
        JsonNode decision = objectMapper.readTree(raw);

        assertEquals("wait", decision.get("decide").asText());
        assertEquals(30, decision.get("wait_minutes").asInt());
        assertEquals("", decision.get("content_for_user").asText());
    }
}
