package com.innercosmos.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleMemoryEmbeddingClient implements MemoryEmbeddingClient {
    /** Default ceilings. The query embed sits on the interactive Aurora turn, so it must fail fast. */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 8000;
    /** Keep one batch comfortably inside the read timeout. */
    private static final int MAX_BATCH_INPUTS = 32;

    private final String apiKey;
    private final String provider;
    private final String model;
    private final String version;
    private final int dimensions;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleMemoryEmbeddingClient(String baseUrl, String apiKey, String model,
                                                  String version, int dimensions, ObjectMapper objectMapper) {
        this(baseUrl, apiKey, model, version, dimensions, objectMapper,
                DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public OpenAiCompatibleMemoryEmbeddingClient(String baseUrl, String apiKey, String model,
                                                  String version, int dimensions, ObjectMapper objectMapper,
                                                  int connectTimeoutMs, int readTimeoutMs) {
        this.apiKey = apiKey; this.provider = providerOf(baseUrl);
        this.model = model; this.version = version; this.dimensions = dimensions;
        this.objectMapper = objectMapper;
        // Spring's default RestClient request factory applies NO read timeout. This client is
        // called synchronously from memory retrieval and capsule matching, i.e. on the request
        // thread of an Aurora turn -- an unresponsive provider socket would hang that turn (and
        // eventually the whole thread pool) forever, and the callers' catch-and-degrade never
        // fires because a hang is not an exception. Bound both phases explicitly.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(Math.max(200, connectTimeoutMs)))
                        .build());
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(500, readTimeoutMs)));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .build();
    }

    @Override public boolean available() { return apiKey != null && !apiKey.isBlank(); }
    @Override public String providerName() { return provider; }
    @Override public String modelName() { return model; }
    @Override public String modelVersion() { return version; }
    @Override public int dimensions() { return dimensions; }

    @Override
    public float[] embed(String text) {
        return request(List.of(text == null ? "" : text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += MAX_BATCH_INPUTS) {
            List<String> chunk = texts.subList(start, Math.min(texts.size(), start + MAX_BATCH_INPUTS))
                    .stream().map(text -> text == null ? "" : text).toList();
            vectors.addAll(request(chunk));
        }
        return vectors;
    }

    /** One provider round-trip for one chunk of inputs. */
    private List<float[]> request(List<String> inputs) {
        if (!available()) throw new IllegalStateException("memory embedding credential is not configured");
        try {
            String body = restClient.post().uri("/embeddings").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("model", model, "input", inputs, "dimensions", dimensions))
                    .retrieve().body(String.class);
            JsonNode data = objectMapper.readTree(body).path("data");
            if (!data.isArray() || data.size() != inputs.size())
                throw new IllegalStateException("embedding provider returned an unexpected batch size");
            // Batch responses are keyed by an "index" field rather than guaranteed to arrive in
            // input order, so honour it when present and fall back to arrival order when a
            // provider omits it. Getting this backwards would silently pair every memory with
            // another memory's vector -- corrupt retrieval that still looks like it works.
            float[][] ordered = new float[inputs.size()][];
            int position = 0;
            for (JsonNode element : data) {
                int index = element.path("index").asInt(-1);
                int slot = index >= 0 && index < inputs.size() && ordered[index] == null ? index : position;
                if (slot >= ordered.length || ordered[slot] != null)
                    throw new IllegalStateException("embedding provider returned an unusable batch layout");
                ordered[slot] = vectorOf(element.path("embedding"));
                position++;
            }
            return List.of(ordered);
        } catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("embedding provider response was invalid", exception); }
    }

    private float[] vectorOf(JsonNode values) {
        if (!values.isArray() || values.size() != dimensions)
            throw new IllegalStateException("embedding provider returned an unexpected vector dimension");
        float[] result = new float[dimensions];
        for (int i = 0; i < dimensions; i++) result[i] = (float) values.get(i).asDouble();
        return result;
    }

    private static String providerOf(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("dashscope") || normalized.contains("aliyun")) return "dashscope";
        if (normalized.contains("openai.com")) return "openai";
        return "openai-compatible";
    }
}
