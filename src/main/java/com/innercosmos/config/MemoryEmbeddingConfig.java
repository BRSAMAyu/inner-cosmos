package com.innercosmos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.DisabledMemoryEmbeddingClient;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.ai.embedding.OpenAiCompatibleMemoryEmbeddingClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "memory.embedding")
public class MemoryEmbeddingConfig {
    public boolean enabled;
    public String apiKey = "";
    public String baseUrl = "https://api.openai.com/v1";
    public String model = "text-embedding-3-small";
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
