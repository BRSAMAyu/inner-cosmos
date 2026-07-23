package com.innercosmos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.DisabledMemoryEmbeddingClient;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.ai.embedding.OpenAiCompatibleMemoryEmbeddingClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defaults point at the real, empirically-confirmed W1 embedding provider: Aliyun DashScope's
 * OpenAI-compatible endpoint with {@code text-embedding-v4}, requested at {@code dimensions=1536}
 * via that model's documented dimension-reduction parameter -- matching, byte-for-byte, the
 * existing fixed {@code tb_memory_embedding.embedding_vector vector(1536)} column (V10), so
 * turning this on requires no new migration or index change. Confirmed via a real HTTP call
 * (200, 1536-length vector) and a full index/retrieval round-trip; see
 * {@code evidence/innovation/INNO-INNER-012/README.md} and
 * {@code MemoryEmbeddingRealProviderIndexRetrievalTest}. Any OpenAI-compatible provider still
 * works by overriding these three env vars -- this is a default, not a hardcoded provider.
 */
@Configuration
@ConfigurationProperties(prefix = "memory.embedding")
public class MemoryEmbeddingConfig {
    public boolean enabled;
    public String apiKey = "";
    public String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public String model = "text-embedding-v4";
    public String version = "2026-01";
    public int dimensions = 1536;

    @Bean
    public MemoryEmbeddingClient memoryEmbeddingClient(ObjectMapper objectMapper) {
        if (!enabled) return new DisabledMemoryEmbeddingClient();
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("MEMORY_EMBEDDING_ENABLED requires MEMORY_EMBEDDING_API_KEY");
        if (dimensions < 8 || dimensions > 1536) throw new IllegalStateException("memory embedding dimensions must be between 8 and 1536");
        return new OpenAiCompatibleMemoryEmbeddingClient(baseUrl, apiKey, model, version, dimensions, objectMapper);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setModel(String model) { this.model = model; }
    public void setVersion(String version) { this.version = version; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
}
