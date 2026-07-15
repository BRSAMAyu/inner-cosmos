package com.innercosmos.ai.embedding;

public interface MemoryEmbeddingClient {
    boolean available();
    String modelName();
    String modelVersion();
    int dimensions();
    float[] embed(String text);
}
