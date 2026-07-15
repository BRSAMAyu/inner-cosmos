package com.innercosmos.ai.embedding;

public class DisabledMemoryEmbeddingClient implements MemoryEmbeddingClient {
    @Override public boolean available() { return false; }
    @Override public String modelName() { return "disabled"; }
    @Override public String modelVersion() { return "disabled"; }
    @Override public int dimensions() { return 0; }
    @Override public float[] embed(String text) { throw new IllegalStateException("memory embedding provider is disabled"); }
}
