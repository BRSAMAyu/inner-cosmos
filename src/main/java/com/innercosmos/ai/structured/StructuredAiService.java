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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Service
public class StructuredAiService {
    private static final Logger log = LoggerFactory.getLogger(StructuredAiService.class);

    /** In-memory counter incremented on every [BAD_AI_OUTPUT] path for test observability. */
    public static final AtomicLong badOutputCounter = new AtomicLong(0);

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
        return call(userId, moduleName, instruction, context, resultType, fallback, null);
    }

    /**
     * Variant that lets the caller override the {@link LlmClient} for this single call.
     * Used by the M6 model router to dispatch a request to a provider-specific client
     * (e.g. GLM or DeepSeek) without rebuilding the singleton client.
     */
    public <T> T call(Long userId, String moduleName, String instruction, Object context,
                      Class<T> resultType, Supplier<T> fallback, LlmClient clientOverride) {
        LlmClient active = clientOverride != null ? clientOverride : llmClient;
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
            request.preferredProvider = preferredProvider(context);
            request.temperature = modeTemperature(context);
            if ("MOCK".equals(assignedGroup)) {
                request.forceMock = true;
            }

            String raw = active.chat(request);
            if (raw == null || raw.isBlank()) {
                log.warn("[BAD_AI_OUTPUT] Structured AI returned blank/null for module {}", moduleName);
                badOutputCounter.incrementAndGet();
                return fallback.get();
            }
            T parsed = StructuredOutputParser.parse(raw, resultType);
            if (parsed != null) {
                success = true;
                return parsed;
            }

            LlmRequest retry = new LlmRequest(userId, moduleName + "_JSON_REPAIR",
                    buildPrompt(instruction, contextJson, raw));
            retry.requestJson = contextJson;
            retry.preferredProvider = preferredProvider(context);
            retry.temperature = modeTemperature(context);
            if ("MOCK".equals(assignedGroup)) {
                retry.forceMock = true;
            }

            String repaired = active.chat(retry);
            parsed = StructuredOutputParser.parse(repaired, resultType);
            if (parsed != null) {
                success = true;
                return parsed;
            }

            log.warn("[BAD_AI_OUTPUT] Structured AI output for {} was not valid JSON after repair (raw truncated): {}",
                    moduleName, truncate(repaired, 500));
            badOutputCounter.incrementAndGet();
            return fallback.get();
        } catch (Exception exception) {
            badOutputCounter.incrementAndGet();
            if (llmConfig.isProdMode()) {
                log.error("Structured AI call for {} failed in prod; returning explicit business fallback: {}",
                        moduleName, exception.getMessage(), exception);
                return fallback.get();
            }
            log.warn("Structured AI call for {} fell back to deterministic extraction: {}",
                    moduleName, exception.getMessage(), exception);
            return fallback.get();
        } finally {
            double latency = System.currentTimeMillis() - startTime;
            try {
                abTestService.recordMetrics(userId, assignedGroup, moduleName, latency, success, !success);
            } catch (Exception e) {
                log.debug("Failed to record A/B test metrics: {}", e.getMessage());
            }
        }
    }

    private String preferredProvider(Object context) {
        if (!(context instanceof Map<?, ?> map)) {
            return null;
        }
        Object value = map.get("preferredProvider");
        if (value == null) {
            value = map.get("aiProviderPreference");
        }
        return value == null ? null : String.valueOf(value);
    }

    /**
     * M-012: read the optional per-mode sampling temperature the caller folded into the
     * context map under {@code "modeTemperature"}. Returns null when absent or
     * non-numeric, so the provider client keeps its existing hardcoded default and
     * non-Aurora calls (whose context carries no such key) stay byte-identical.
     */
    private Double modeTemperature(Object context) {
        if (!(context instanceof Map<?, ?> map)) {
            return null;
        }
        Object value = map.get("modeTemperature");
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    public String getCurrentTestGroup(Long userId) {
        return abTestService.getUserGroup(userId, null);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "<null>";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String buildPrompt(String instruction, String contextJson, String invalidOutput) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are an Inner Cosmos structured reasoning worker.
                Return only valid JSON matching the requested schema.
                Do not wrap the JSON in markdown.
                Do not include <think>, analysis, comments, or any text outside the JSON object.
                Inside JSON string values, prefer Chinese corner quotes instead of raw ASCII double quotes.
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
