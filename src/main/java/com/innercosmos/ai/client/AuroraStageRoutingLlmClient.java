package com.innercosmos.ai.client;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;

/**
 * Routes Aurora's temporal layers to independently configured model clients.
 *
 * <p>The fast acknowledgement, visible speaker and reflective planner are different
 * cognitive jobs. This router keeps that distinction at the provider boundary while
 * preserving the caller-selected client for every non-Aurora module and for any stage
 * whose dedicated credential is unavailable.
 */
public final class AuroraStageRoutingLlmClient implements LlmClient {
    private final LlmClient fallback;
    private final LlmClient fast;
    private final LlmClient speaker;
    private final LlmClient thinker;
    private final StageProfile fastProfile;
    private final StageProfile speakerProfile;
    private final StageProfile plannerProfile;
    private final StageProfile criticProfile;

    public AuroraStageRoutingLlmClient(LlmClient fallback, LlmClient fast,
                                       LlmClient speaker, LlmClient thinker) {
        this(fallback, fast, speaker, thinker,
                new StageProfile(false, "minimal", 0.25, 256),
                // The planner has already done the reflective work. Asking the visible speaker
                // to reason again duplicated latency and repeatedly crossed the classroom
                // eight-second deadline. Keep this stage expressive, but make it a bounded
                // plan-to-language pass.
                new StageProfile(false, "minimal", 0.78, 2_048),
                new StageProfile(true, "high", 0.10, 8_192),
                new StageProfile(true, "high", 0.05, 2_048));
    }

    public AuroraStageRoutingLlmClient(LlmClient fallback, LlmClient fast,
                                       LlmClient speaker, LlmClient thinker,
                                       StageProfile fastProfile, StageProfile speakerProfile,
                                       StageProfile plannerProfile, StageProfile criticProfile) {
        this.fallback = fallback;
        this.fast = fast;
        this.speaker = speaker;
        this.thinker = thinker;
        this.fastProfile = fastProfile;
        this.speakerProfile = speakerProfile;
        this.plannerProfile = plannerProfile;
        this.criticProfile = criticProfile;
    }

    @Override
    public String chat(LlmRequest request) {
        applyStageThinkingContract(request);
        return delegate(request).chat(request);
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        applyStageThinkingContract(request);
        return delegate(request).streamChat(request);
    }

    private LlmClient delegate(LlmRequest request) {
        String module = module(request);
        if (module.startsWith("AURORA_FOREGROUND_")) return fast == null ? fallback : fast;
        if (module.startsWith("AURORA_SPEAKER_")
                || module.startsWith("AURORA_INNER_VOICE_")) {
            return speaker == null ? fallback : speaker;
        }
        if (module.startsWith("AURORA_PLAN_") || module.startsWith("AURORA_CRITIC_")) {
            return thinker == null ? fallback : thinker;
        }
        return fallback;
    }

    private void applyStageThinkingContract(LlmRequest request) {
        if (request == null) return;
        String module = module(request);
        if (module.startsWith("AURORA_FOREGROUND_")) {
            apply(request, fastProfile);
        } else if (module.startsWith("AURORA_SPEAKER_")
                || module.startsWith("AURORA_INNER_VOICE_")) {
            apply(request, speakerProfile);
        } else if (module.startsWith("AURORA_PLAN_")) {
            apply(request, plannerProfile);
        } else if (module.startsWith("AURORA_CRITIC_")) {
            apply(request, criticProfile);
        }
    }

    private void apply(LlmRequest request, StageProfile profile) {
        if (profile == null) return;
        request.thinkingEnabled = profile.thinkingEnabled();
        request.reasoningEffort = profile.reasoningEffort();
        request.temperature = profile.temperature();
        if (profile.maxTokens() > 0) request.maxTokens = profile.maxTokens();
    }

    private String module(LlmRequest request) {
        return request == null || request.moduleName == null
                ? "" : request.moduleName.toUpperCase(Locale.ROOT);
    }

    public record StageProfile(boolean thinkingEnabled, String reasoningEffort,
                               double temperature, int maxTokens) {}
}
