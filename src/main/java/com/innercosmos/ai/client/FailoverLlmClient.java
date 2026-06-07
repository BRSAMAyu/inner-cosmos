package com.innercosmos.ai.client;

import com.innercosmos.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class FailoverLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(FailoverLlmClient.class);

    private final List<ProviderCandidate> candidates;
    private final Executor aiExecutor;

    public FailoverLlmClient(List<ProviderCandidate> candidates, Executor aiExecutor) {
        this.candidates = candidates == null ? List.of() : candidates;
        this.aiExecutor = aiExecutor;
    }

    @Override
    public String chat(LlmRequest request) {
        List<String> errors = new ArrayList<>();
        for (ProviderCandidate candidate : orderedCandidates(request)) {
            try {
                if (candidate.client == null) continue;
                log.info("LLM attempt provider={} model={} module={}", candidate.provider, candidate.model, request.moduleName);
                return candidate.client.chat(request);
            } catch (Exception exception) {
                String message = candidate.provider + "/" + candidate.model + ": " + exception.getMessage();
                errors.add(message);
                log.warn("LLM provider attempt failed: {}", message);
            }
        }
        throw new AiProviderException("All LLM providers failed: " + String.join(" | ", errors));
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        aiExecutor.execute(() -> {
            try {
                String response = chat(request);
                for (String token : response.split("")) {
                    emitter.send(SseEmitter.event().name("token").data("{\"content\":\"" + escape(token) + "\"}"));
                    Thread.sleep(18);
                }
                emitter.send(SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    private List<ProviderCandidate> orderedCandidates(LlmRequest request) {
        if (request == null || request.preferredProvider == null || request.preferredProvider.isBlank()) {
            return candidates;
        }
        String preferred = request.preferredProvider.trim();
        List<ProviderCandidate> ordered = new ArrayList<>();
        for (ProviderCandidate candidate : candidates) {
            if (candidate.provider.equalsIgnoreCase(preferred)) ordered.add(candidate);
        }
        for (ProviderCandidate candidate : candidates) {
            if (!candidate.provider.equalsIgnoreCase(preferred)) ordered.add(candidate);
        }
        return ordered;
    }

    private String escape(String token) {
        return token.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record ProviderCandidate(String provider, String model, LlmClient client) {
    }
}
