package com.innercosmos.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class OpenAiCompatibleLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private final LlmConfig config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockFallback mockFallback = new MockFallback();
    private final Executor executor;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public OpenAiCompatibleLlmClient(LlmConfig config, Executor executor) {
        this.config = config;
        this.executor = executor;
        String baseUrl = config.baseUrl == null || config.baseUrl.isBlank() ? defaultBaseUrl(config.provider) : config.baseUrl;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String chat(LlmRequest request) {
        if (config.apiKey == null || config.apiKey.isBlank()) {
            return mockFallback.reply(request.prompt);
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "你是 Inner Cosmos 的 Aurora.保持温柔、克制、安全边界."));
            if (request.recentMessages != null) {
                for (String recent : request.recentMessages) {
                    if (recent != null && !recent.isBlank()) {
                        messages.add(Map.of("role", "user", "content", "Context note: " + recent));
                    }
                }
            }
            messages.add(Map.of("role", "user", "content", request.prompt == null ? "" : request.prompt));
            Map<String, Object> payload = Map.of(
                    "model", config.model == null || config.model.isBlank() ? defaultModel(config.provider) : config.model,
                    "messages", messages,
                    "temperature", 0.7,
                    "stream", false
            );
            String body = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + config.apiKey)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? mockFallback.reply(request.prompt) : content.asText();
        } catch (Exception exception) {
            log.warn("OpenAI-compatible client ({}) chat failed, falling back to mock: {}", config.provider, exception.getMessage());
            return mockFallback.reply(request.prompt);
        }
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        executor.execute(() -> {
            try {
                if (config.apiKey == null || config.apiKey.isBlank()) {
                    // No key: drip the mock fallback over real SSE transport.
                    drip(emitter, mockFallback.reply(request.prompt));
                    return;
                }
                String aggregated = streamRemote(request, emitter);
                dripTail(emitter, aggregated);
            } catch (Exception exception) {
                emitter.completeWithError(new AiProviderException("remote stream failed and fallback stream failed"));
            }
        });
        return emitter;
    }

    /**
     * Real provider SSE streaming (VS-003 §2). OpenAI-compatible providers stream via
     * {@code stream:true} with {@code delta.content} chunks. Falls back to a single
     * full-text drip (real SSE transport) on any provider failure.
     */
    private String streamRemote(LlmRequest request, SseEmitter emitter) {
        String baseUrl = config.baseUrl == null || config.baseUrl.isBlank() ? defaultBaseUrl(config.provider) : config.baseUrl;
        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "你是 Inner Cosmos 的 Aurora.保持温柔、克制、安全边界."));
        if (request.recentMessages != null) {
            for (String recent : request.recentMessages) {
                if (recent != null && !recent.isBlank()) {
                    messages.add(Map.of("role", "user", "content", "Context note: " + recent));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", request.prompt == null ? "" : request.prompt));
        Map<String, Object> payload = Map.of(
                "model", config.model == null || config.model.isBlank() ? defaultModel(config.provider) : config.model,
                "messages", messages,
                "temperature", 0.7,
                "stream", true
        );
        try {
            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey)
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(30))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            java.util.stream.Stream<String> lines = httpClient.send(httpRequest,
                    java.net.http.HttpResponse.BodyHandlers.ofLines()).body();
            StringBuilder aggregated = new StringBuilder();
            lines.forEach(line -> {
                if (line == null || !line.startsWith("data:")) return;
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) return;
                try {
                    JsonNode delta = objectMapper.readTree(data)
                            .path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.asText().isEmpty()) {
                        String token = delta.asText();
                        aggregated.append(token);
                        emitter.send(SseEmitter.event().name("token")
                                .data("{\"content\":\"" + escape(token) + "\"}"));
                    }
                } catch (Exception parseEx) {
                    log.debug("OpenAI-compat ({}) stream parse skip: {}", config.provider, parseEx.getMessage());
                }
            });
            return aggregated.toString();
        } catch (Exception remoteError) {
            log.warn("OpenAI-compatible client ({}) stream failed, falling back to chat drip: {}", config.provider, remoteError.getMessage());
            return chat(request);
        }
    }

    /** If nothing streamed (empty/missing), drip the full text so the client still sees content. */
    private void dripTail(SseEmitter emitter, String text) throws Exception {
        if (text != null && !text.isEmpty()) {
            drip(emitter, text);
            return;
        }
        emitter.send(SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
        emitter.complete();
    }

    private void drip(SseEmitter emitter, String text) throws Exception {
        for (String token : (text == null ? "" : text).split("")) {
            emitter.send(SseEmitter.event().name("token").data("{\"content\":\"" + escape(token) + "\"}"));
        }
        emitter.send(SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
        emitter.complete();
    }

    private String defaultBaseUrl(String provider) {
        if ("deepseek".equalsIgnoreCase(provider)) return "https://api.deepseek.com";
        if ("glm".equalsIgnoreCase(provider)) return "https://open.bigmodel.cn/api/paas/v4";
        if ("minimax".equalsIgnoreCase(provider)) return "https://api.minimax.chat/v1";
        return "https://api.openai.com/v1";
    }

    private String defaultModel(String provider) {
        if ("deepseek".equalsIgnoreCase(provider)) return "deepseek-chat";
        if ("glm".equalsIgnoreCase(provider)) return "glm-4-flash";
        if ("minimax".equalsIgnoreCase(provider)) return "MiniMax-Text-01";
        return "gpt-4o-mini";
    }

    private String escape(String token) {
        return token.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static class MockFallback {
        String reply(String prompt) {
            if (prompt != null && prompt.contains("拖延")) {
                return "我们先不把拖延解释成失败.也许可以从一个十分钟的小动作开始.";
            }
            return "我在.远程模型暂时不可用,但我们仍然可以先把这件事温柔地整理清楚.";
        }
    }
}
