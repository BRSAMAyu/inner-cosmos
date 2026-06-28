package com.innercosmos.ai.router;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves which {@link LlmClient} to use for a given (userId, sessionId).
 *
 * <p>Resolution order (most specific wins):
 * <ol>
 *   <li>{@code tb_dialog_session.preferred_model} (set per-conversation)</li>
 *   <li>{@code tb_user_profile.preferred_model} (set per-user)</li>
 *   <li>{@code llm.provider} system default</li>
 * </ol>
 *
 * <p>The returned client is the raw provider client from the {@code namedLlmClients}
 * bean, so a {@code forceMock} A/B-test assignment still works as before — but the
 * router's job here is just "which client", not "what A/B bucket".
 */
@Component
public class SessionModelRouter {
    private static final Logger log = LoggerFactory.getLogger(SessionModelRouter.class);

    @Autowired(required = false)
    @Qualifier("namedLlmClients")
    private Map<String, LlmClient> named;

    @Autowired
    private DialogSessionMapper sessionMapper;

    @Autowired
    private UserProfileMapper userMapper;

    @Autowired
    private LlmConfig llmConfig;

    /**
     * Resolve the LLM client for a (user, session) pair. The provider string in
     * the result is the upper-case key the caller used (e.g. {@code "MINIMAX"}),
     * or the system default if neither row set a preference.
     */
    public ResolvedModel resolve(Long userId, Long sessionId) {
        String chosen = null;
        if (sessionId != null) {
            DialogSession s = sessionMapper.selectById(sessionId);
            if (s != null) chosen = s.preferredModel;
        }
        if ((chosen == null || chosen.isBlank()) && userId != null) {
            UserProfile u = userMapper.selectById(userId);
            if (u != null) chosen = u.preferredModel;
        }
        if (chosen == null || chosen.isBlank()) {
            chosen = llmConfig.activeProvider().toUpperCase();
        }
        String normalized = chosen.toUpperCase();
        // A stored preference may point at a provider that is no longer wired (no API key,
        // e.g. a seeded "DEEPSEEK"). namedLlmClients only contains keyed providers + MOCK, so
        // if the preference isn't available, resolve to the system default provider cleanly —
        // both the reported provider name and the model name then match the client actually used.
        if (named == null || !named.containsKey(normalized)) {
            normalized = llmConfig.activeProvider().toUpperCase();
        }
        LlmClient client = pick(normalized);
        if (client == null) {
            log.warn("SessionModelRouter: no client found for provider {}, falling back to system default", normalized);
            return new ResolvedModel(llmConfig.activeProvider().toUpperCase(),
                    llmConfig.activeModel(),
                    named == null ? null : named.get(llmConfig.activeProvider().toUpperCase()));
        }
        return new ResolvedModel(normalized, modelNameFor(normalized), client);
    }

    /**
     * Set (or clear, if {@code provider} is null/blank) the per-session model preference.
     */
    public void setSessionPreference(Long sessionId, String provider) {
        if (sessionId == null) return;
        DialogSession s = sessionMapper.selectById(sessionId);
        if (s == null) return;
        s.preferredModel = (provider == null || provider.isBlank()) ? null : provider.toUpperCase();
        sessionMapper.updateById(s);
    }

    private LlmClient pick(String provider) {
        if (named == null) return null;
        String key = provider.toUpperCase();
        if (named.containsKey(key)) return named.get(key);
        String fallback = llmConfig.activeProvider().toUpperCase();
        return named.get(fallback);
    }

    private String modelNameFor(String provider) {
        if (provider == null) return llmConfig.activeModel();
        return switch (provider.toUpperCase()) {
            case "MINIMAX" -> llmConfig.minimax.model;
            case "MIMO"    -> llmConfig.mimo.model;
            case "GLM"     -> llmConfig.glm.model;
            case "DEEPSEEK"-> llmConfig.deepseek.model;
            case "MOCK"    -> "mock-inner-cosmos";
            default        -> llmConfig.model;
        };
    }
}
