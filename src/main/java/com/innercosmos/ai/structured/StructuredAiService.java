package com.innercosmos.ai.structured;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.prompt.StructuredOutputParser;
import com.innercosmos.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class StructuredAiService {
    private static final Logger log = LoggerFactory.getLogger(StructuredAiService.class);

    private final LlmClient llmClient;

    public StructuredAiService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public <T> T call(Long userId, String moduleName, String instruction, Object context,
                      Class<T> resultType, Supplier<T> fallback) {
        String contextJson = JsonUtils.toJson(context);
        String prompt = buildPrompt(instruction, contextJson, null);
        try {
            LlmRequest request = new LlmRequest(userId, moduleName, prompt);
            request.requestJson = contextJson;
            String raw = llmClient.chat(request);
            T parsed = StructuredOutputParser.parse(raw, resultType);
            if (parsed != null) {
                return parsed;
            }

            LlmRequest retry = new LlmRequest(userId, moduleName + "_JSON_REPAIR",
                    buildPrompt(instruction, contextJson, raw));
            retry.requestJson = contextJson;
            String repaired = llmClient.chat(retry);
            parsed = StructuredOutputParser.parse(repaired, resultType);
            if (parsed != null) {
                return parsed;
            }
            log.warn("Structured AI output for {} could not be parsed after repair", moduleName);
        } catch (Exception exception) {
            log.warn("Structured AI call for {} fell back to deterministic extraction: {}", moduleName, exception.getMessage());
        }
        return fallback.get();
    }

    private String buildPrompt(String instruction, String contextJson, String invalidOutput) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are an Inner Cosmos structured reasoning worker.
                Return only valid JSON matching the requested schema.
                Do not wrap the JSON in markdown.
                Do not diagnose the user, reveal private identity, or claim certainty.
                """.trim());
        prompt.append("\n\nTask:\n").append(instruction == null ? "" : instruction);
        prompt.append("\n\nInput JSON:\n").append(contextJson == null ? "{}" : contextJson);
        if (invalidOutput != null && !invalidOutput.isBlank()) {
            prompt.append("\n\nThe previous output was not valid JSON for the schema. Repair it without changing the intended content:\n")
                    .append(invalidOutput);
        }
        return prompt.toString();
    }
}
