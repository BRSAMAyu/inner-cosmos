package com.innercosmos.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

public class OpenAiCompatibleMemoryEmbeddingClient implements MemoryEmbeddingClient {
    private final String apiKey;
    private final String model;
    private final String version;
    private final int dimensions;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleMemoryEmbeddingClient(String baseUrl, String apiKey, String model,
                                                  String version, int dimensions, ObjectMapper objectMapper) {
        this.apiKey = apiKey; this.model = model; this.version = version; this.dimensions = dimensions;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(baseUrl.replaceAll("/+$", "")).build();
    }

    @Override public boolean available() { return apiKey != null && !apiKey.isBlank(); }
    @Override public String modelName() { return model; }
    @Override public String modelVersion() { return version; }
    @Override public int dimensions() { return dimensions; }

    @Override
    public float[] embed(String text) {
        if (!available()) throw new IllegalStateException("memory embedding credential is not configured");
        try {
            String body = restClient.post().uri("/embeddings").contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("model", model, "input", text == null ? "" : text, "dimensions", dimensions))
                    .retrieve().body(String.class);
            JsonNode values = objectMapper.readTree(body).path("data").path(0).path("embedding");
            if (!values.isArray() || values.size() != dimensions)
                throw new IllegalStateException("embedding provider returned an unexpected vector dimension");
            float[] result = new float[dimensions];
            for (int i = 0; i < dimensions; i++) result[i] = (float) values.get(i).asDouble();
            return result;
        } catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("embedding provider response was invalid", exception); }
    }
}
