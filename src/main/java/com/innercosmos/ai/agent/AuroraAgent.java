package com.innercosmos.ai.agent;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.prompt.PromptBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuroraAgent {
    private final LlmClient llmClient;

    public AuroraAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String reply(Long userId, String input, List<String> recentMessages, String voiceMetadata) {
        String prompt = new PromptBuilder()
                .withSystemBoundary()
                .withRecentMessages(recentMessages)
                .withVoiceMetadata(voiceMetadata)
                .withUserInput(input)
                .withOutputSchema()
                .build();
        return llmClient.chat(new LlmRequest(userId, "AURORA_CHAT", prompt));
    }
}
