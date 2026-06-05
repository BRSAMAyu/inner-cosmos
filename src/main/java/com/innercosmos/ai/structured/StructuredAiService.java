package com.innercosmos.ai.structured;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.prompt.StructuredOutputParser;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import com.innercosmos.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class StructuredAiService {
    private static final Logger log = LoggerFactory.getLogger(StructuredAiService.class);

    private final LlmClient llmClient;
    private final ABTestService abTestService;
    private final LlmConfig llmConfig;

    public StructuredAiService(LlmClient llmClient, ABTestService abTestService, LlmConfig llmConfig) {
        this.llmClient = llmClient;
        this.abTestService = abTestService;
        this.llmConfig = llmConfig;
    }

    public <T> T call(Long userId, String moduleName, String instruction, Object context,
                      Class<T> resultType, Supplier<T> fallback) {
        // A/B test group assignment. Production must never route the main product
        // experience into mock through experiments.
        String assignedGroup = abTestService.assignGroup(userId, moduleName);
        if (llmConfig.isProdMode()) {
            assignedGroup = "REMOTE";
        }
        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            String contextJson = JsonUtils.toJson(context);
            String prompt = buildPrompt(instruction, contextJson, null);

            LlmRequest request = new LlmRequest(userId, moduleName, prompt);
            request.requestJson = contextJson;

            // Force group if A/B test is active
            if ("MOCK".equals(assignedGroup)) {
                request.forceMock = true;
            }

            String raw = llmClient.chat(request);
            T parsed = StructuredOutputParser.parse(raw, resultType);
            if (parsed != null) {
                success = true;
                return parsed;
            }

            // JSON repair attempt
            LlmRequest retry = new LlmRequest(userId, moduleName + "_JSON_REPAIR",
                    buildPrompt(instruction, contextJson, raw));
            retry.requestJson = contextJson;
            if ("MOCK".equals(assignedGroup)) {
                retry.forceMock = true;
            }

            String repaired = llmClient.chat(retry);
            parsed = StructuredOutputParser.parse(repaired, resultType);
            if (parsed != null) {
                success = true;
                return parsed;
            }
            log.warn("Structured AI output for {} could not be parsed after repair", moduleName);
            if (llmConfig.isProdMode()) {
                throw new IllegalStateException("真实 AI 输出不是合法 JSON，生产模式不会静默回退 Mock");
            }

        } catch (Exception exception) {
            if (llmConfig.isProdMode()) {
                throw new IllegalStateException("真实 AI 调用失败，生产模式不会静默回退 Mock：" + exception.getMessage(), exception);
            }
            log.warn("Structured AI call for {} fell back to deterministic extraction: {}", moduleName, exception.getMessage());
        } finally {
            // Record A/B test metrics
            double latency = System.currentTimeMillis() - startTime;
            try {
                abTestService.recordMetrics(userId, assignedGroup, moduleName, latency, success, !success);
            } catch (Exception e) {
                // Don't let metric recording affect the main flow
                log.debug("Failed to record A/B test metrics: {}", e.getMessage());
            }
        }

        return fallback.get();
    }

    /**
     * Check if A/B testing is active for current user.
     */
    public String getCurrentTestGroup(Long userId) {
        return abTestService.getUserGroup(userId, null);
    }

    private String buildPrompt(String instruction, String contextJson, String invalidOutput) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are an Inner Cosmos structured reasoning worker.
                Return only valid JSON matching the requested schema.
                Do not wrap the JSON in markdown.
                Do not include <think>, analysis, comments, or any text outside the JSON object.
                Inside JSON string values, use Chinese quotation marks 「」 instead of raw ASCII double quotes.
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
