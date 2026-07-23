package com.innercosmos.ai.embedding;

/**
 * Gemini audit 2.6 (CONFIRMED/P1): thrown when a vector about to be written to or queried against
 * {@code tb_memory_embedding.embedding_vector} (a fixed-width PostgreSQL {@code vector(1536)}
 * column, see V10__versioned_memory_embeddings.sql) does not match the column's declared
 * dimension. Embedding dimension is part of the embedding-model-version contract: a model/config
 * change that produces a different vector length must fail fast here, never be silently
 * zero-padded or truncated to force-fit the fixed column width.
 * <p>
 * Callers (MemoryEmbeddingIndexServiceImpl) already treat any exception from an embedding
 * operation as "embeddings unavailable for this turn" (falls back to the deterministic lexical
 * path) or "this one card failed to index" (counted, not fatal) -- this intentionally reuses that
 * existing handling rather than requiring new plumbing.
 */
public class EmbeddingDimensionMismatchException extends IllegalStateException {
    public EmbeddingDimensionMismatchException(String message) {
        super(message);
    }
}
