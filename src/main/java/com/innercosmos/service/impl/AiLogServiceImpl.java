package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.mapper.AiInteractionLogMapper;
import com.innercosmos.service.AiLogService;
import com.innercosmos.util.DataMaskingUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
public class AiLogServiceImpl implements AiLogService {
    private final AiInteractionLogMapper mapper;
    private final MeterRegistry meterRegistry;

    public AiLogServiceImpl(AiInteractionLogMapper mapper, MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void record(Long userId, String moduleName, String prompt, String response, boolean success, long latencyMs) {
        recordDetailed(userId, moduleName, "UNKNOWN", "unknown", prompt, response, null, null,
                success, false, success ? null : "AI call failed", latencyMs);
    }

    @Override
    public void recordDetailed(Long userId, String moduleName, String provider, String modelName,
                               String prompt, String response, String requestJson, String responseJson,
                               boolean success, boolean fallbackUsed, String errorMessage, long latencyMs) {
        AiInteractionLog log = new AiInteractionLog();
        log.userId = userId;
        log.moduleName = moduleName;
        log.provider = provider == null || provider.isBlank() ? "UNKNOWN" : provider;
        log.modelName = modelName == null || modelName.isBlank() ? "unknown" : modelName;
        log.requestPrompt = prompt;
        // M4: redact the model RESPONSE (text + json) before it is persisted to
        // tb_ai_interaction_log. The prompt/requestJson are already masked upstream by
        // ABTestLlmClientWrapper.redact(); the response was not, so if the model echoed a
        // phone/email it landed at rest unmasked. Reuse the SAME masking helper here.
        // Only the LOGGED copy is masked — the live response returned to the caller is unaffected.
        log.responseText = response == null ? null : DataMaskingUtils.maskContact(response);
        log.requestJson = requestJson;
        log.responseJson = responseJson == null ? null : DataMaskingUtils.maskContact(responseJson);
        log.success = success;
        log.fallbackUsed = fallbackUsed;
        log.errorMessage = errorMessage;
        log.latencyMs = latencyMs;
        log.tokenInputEstimate = prompt == null ? 0 : Math.max(1, prompt.length() / 2);
        log.tokenOutputEstimate = response == null ? 0 : Math.max(1, response.length() / 2);
        try {
            mapper.insert(log);
        } finally {
            recordMetrics(log);
        }
    }

    @Override
    public List<AiInteractionLog> listRecent(Long userId) {
        return listRecent(userId, null, null, null);
    }

    @Override
    public List<AiInteractionLog> listRecent(Long userId, String moduleName, String provider, Boolean success) {
        QueryWrapper<AiInteractionLog> query = new QueryWrapper<>();
        if (userId != null) {
            query.eq("user_id", userId);
        }
        if (moduleName != null && !moduleName.isBlank()) {
            query.eq("module_name", moduleName);
        }
        if (provider != null && !provider.isBlank()) {
            query.eq("provider", provider);
        }
        if (success != null) {
            query.eq("success", success);
        }
        query.orderByDesc("id").last("LIMIT 100");
        return mapper.selectList(query);
    }

    // Regression (Gemini audit / remaining-work-handoff.md 2.2.5): this was previously
    // unscoped by user, and /api/ai/health -- callable by ANY authenticated user, not just admin
    // (ThoughtShredderSection legitimately relies on that) -- surfaced whichever row this returned
    // as "the last AI call", leaking another user's module/provider/model/error/latency metadata.
    @Override
    public AiInteractionLog latest(Long userId) {
        QueryWrapper<AiInteractionLog> query = new QueryWrapper<>();
        if (userId != null) {
            query.eq("user_id", userId);
        }
        query.orderByDesc("id").last("LIMIT 1");
        return mapper.selectOne(query);
    }

    private void recordMetrics(AiInteractionLog log) {
        String outcome = Boolean.TRUE.equals(log.success) ? "success" : "error";
        Tags base = Tags.of(
                "module", bounded(log.moduleName),
                "provider", bounded(log.provider),
                "outcome", outcome,
                "fallback", Boolean.toString(Boolean.TRUE.equals(log.fallbackUsed)));
        meterRegistry.counter("inner.cosmos.ai.provider.calls", base).increment();
        meterRegistry.timer("inner.cosmos.ai.provider.latency", base)
                .record(Duration.ofMillis(Math.max(0L, log.latencyMs == null ? 0L : log.latencyMs)));
        meterRegistry.counter("inner.cosmos.ai.tokens.estimated",
                        base.and("direction", "input"))
                .increment(Math.max(0, log.tokenInputEstimate == null ? 0 : log.tokenInputEstimate));
        meterRegistry.counter("inner.cosmos.ai.tokens.estimated",
                        base.and("direction", "output"))
                .increment(Math.max(0, log.tokenOutputEstimate == null ? 0 : log.tokenOutputEstimate));
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 48));
    }
}
