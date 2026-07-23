package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.EmbeddingDimensionMismatchException;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryEmbeddingIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MemoryEmbeddingIndexServiceImpl implements MemoryEmbeddingIndexService {
    private static final Logger log = LoggerFactory.getLogger(MemoryEmbeddingIndexServiceImpl.class);
    /**
     * Gemini audit 2.6 (CONFIRMED/P1): tb_memory_embedding.embedding_vector is a fixed-width
     * PostgreSQL {@code vector(1536)} column (V10__versioned_memory_embeddings.sql). Embedding
     * dimension is part of the embedding-model-version contract -- {@code
     * memory.embedding.dimensions} is operator-configurable in [8,1536] (MemoryEmbeddingConfig),
     * so a real vector whose length does not equal this exact column width must be rejected,
     * never silently zero-padded (if shorter) or truncated (if longer) to force-fit.
     */
    static final int MEMORY_EMBEDDING_VECTOR_COLUMN_DIMENSION = 1536;
    private final MemoryEmbeddingClient client;
    private final MemoryEmbeddingMapper mapper;
    private final MemoryCardMapper memoryMapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private volatile Boolean postgres;

    public MemoryEmbeddingIndexServiceImpl(MemoryEmbeddingClient client, MemoryEmbeddingMapper mapper,
                                           MemoryCardMapper memoryMapper, JdbcTemplate jdbc,
                                           ObjectMapper objectMapper) {
        this.client = client; this.mapper = mapper; this.memoryMapper = memoryMapper;
        this.jdbc = jdbc; this.objectMapper = objectMapper;
    }

    @Override
    public Map<Long, Double> similarities(Long userId, String query, List<MemoryCard> allowedCurrentCards) {
        if (!client.available() || query == null || query.isBlank() || allowedCurrentCards.isEmpty()) return Map.of();
        try {
            List<MemoryCard> providerEligible = allowedCurrentCards.stream()
                    .filter(card -> !"LOCAL_ONLY".equalsIgnoreCase(safe(card.consentScope))
                            && !"NO_EXTERNAL_PROCESSING".equalsIgnoreCase(safe(card.consentScope)))
                    .toList();
            if (providerEligible.isEmpty()) return Map.of();
            float[] queryVector = client.embed(query);
            return isPostgres() ? postgresScores(userId, queryVector) : localScores(userId, queryVector, providerEligible);
        } catch (Exception failure) {
            // Embeddings only widen candidate quality. Relational privacy/status gates and the
            // deterministic lexical path remain available; never log private text or credentials.
            log.warn("Memory embedding candidate source unavailable: {}", failure.getClass().getSimpleName());
            return Map.of();
        }
    }

    @Override
    public RebuildResult rebuildMissing(int requestedBatchSize) {
        if (!client.available()) return new RebuildResult(0, 0, 0, 0);
        int batchSize = Math.max(1, Math.min(500, requestedBatchSize));
        List<Long> ids = mapper.selectMissingMemoryIds(client.modelName(), client.modelVersion(), batchSize);
        if (ids.isEmpty()) return new RebuildResult(0, 0, 0, 0);
        int indexed = 0;
        int failed = 0;
        for (MemoryCard card : memoryMapper.selectBatchIds(ids)) {
            try {
                if (indexIfMissing(card.userId, card)) indexed++;
            } catch (Exception failure) {
                failed++;
                // Never log memory content, provider response bodies, or credentials.
                log.warn("Memory embedding rebuild failed for memory {}: {}", card.id,
                        failure.getClass().getSimpleName());
            }
        }
        return new RebuildResult(ids.size(), indexed, failed, pendingCount());
    }

    @Override
    public long pendingCount() {
        if (!client.available()) return 0;
        return mapper.countMissing(client.modelName(), client.modelVersion());
    }

    private boolean indexIfMissing(Long userId, MemoryCard card) throws Exception {
        if (!"ACTIVE".equalsIgnoreCase(safe(card.status))
                || "LOCAL_ONLY".equalsIgnoreCase(safe(card.consentScope))
                || "NO_EXTERNAL_PROCESSING".equalsIgnoreCase(safe(card.consentScope))) return false;
        int sourceVersion = card.versionNo == null ? 1 : card.versionNo;
        QueryWrapper<MemoryEmbedding> query = new QueryWrapper<MemoryEmbedding>()
                .eq("user_id", userId).eq("memory_id", card.id).eq("model_name", client.modelName())
                .eq("model_version", client.modelVersion()).eq("source_version", sourceVersion)
                .eq("task_scope", "GENERAL").eq("status", "ACTIVE");
        if (mapper.selectCount(query) > 0) return false;
        float[] vector = client.embed(String.join(" ", safe(card.title), safe(card.summary),
                safe(card.keywordTags), safe(card.peopleTags)));
        MemoryEmbedding row = new MemoryEmbedding();
        row.userId = userId; row.memoryId = card.id; row.modelName = client.modelName();
        row.modelVersion = client.modelVersion(); row.sourceVersion = sourceVersion; row.taskScope = "GENERAL";
        row.dimensions = vector.length; row.embeddingJson = objectMapper.writeValueAsString(vector); row.status = "ACTIVE";
        if (isPostgres()) {
            // Gemini audit 2.6: fail fast BEFORE inserting anything -- a dimension mismatch here
            // must not create an embedding_json row with no matching (or a corrupted) vector.
            requireDimensionContract(vector);
        }
        try { mapper.insert(row); } catch (DuplicateKeyException race) { return false; }
        if (isPostgres()) {
            try {
                jdbc.update("UPDATE tb_memory_embedding SET embedding_vector=?::vector WHERE id=?",
                        vectorLiteral(vector), row.id);
            } catch (RuntimeException vectorWriteFailure) {
                mapper.deleteById(row.id);
                throw vectorWriteFailure;
            }
        }
        return true;
    }

    /**
     * Gemini audit 2.6 (CONFIRMED/P1): the embedding-model-version -> dimension contract, checked
     * at the one place a vector is about to be written to or compared against the fixed-width
     * PostgreSQL column. Throws rather than silently zero-padding/truncating to force-fit.
     */
    private static void requireDimensionContract(float[] vector) {
        if (vector.length != MEMORY_EMBEDDING_VECTOR_COLUMN_DIMENSION) {
            throw new EmbeddingDimensionMismatchException(
                    "embedding dimension contract violation: got a " + vector.length
                    + "-dimension vector but tb_memory_embedding.embedding_vector is a fixed "
                    + MEMORY_EMBEDDING_VECTOR_COLUMN_DIMENSION + "-dimension pgvector column (V10). "
                    + "A model/dimension change requires an explicit expand-contract migration "
                    + "(new column/table, backfill, dual-read, index rebuild) -- never a silently "
                    + "zero-padded or truncated write.");
        }
    }

    private Map<Long, Double> postgresScores(Long userId, float[] queryVector) {
        // Gemini audit 2.6: the query vector is compared against the SAME fixed-width column, so
        // it is held to the same fail-fast contract -- never silently padded/truncated to compare
        // apples to oranges against stored 1536-dimension vectors.
        requireDimensionContract(queryVector);
        String vector = vectorLiteral(queryVector);
        Map<Long, Double> result = new HashMap<>();
        jdbc.query("""
                SELECT e.memory_id, 1 - (e.embedding_vector <=> ?::vector) AS score
                FROM tb_memory_embedding e
                JOIN tb_memory_card c ON c.id=e.memory_id AND c.user_id=e.user_id
                  AND c.version_no=e.source_version AND c.status='ACTIVE'
                WHERE e.user_id=? AND e.model_name=? AND e.model_version=?
                  AND e.task_scope='GENERAL' AND e.status='ACTIVE' AND e.embedding_vector IS NOT NULL
                ORDER BY e.embedding_vector <=> ?::vector LIMIT 100
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                        row -> result.put(row.getLong("memory_id"), clamp(row.getDouble("score"))),
                vector, userId, client.modelName(), client.modelVersion(), vector);
        return result;
    }

    private Map<Long, Double> localScores(Long userId, float[] queryVector, List<MemoryCard> cards) throws Exception {
        Map<Long, Double> result = new HashMap<>();
        for (MemoryCard card : cards) {
            MemoryEmbedding row = mapper.selectOne(new QueryWrapper<MemoryEmbedding>()
                    .eq("user_id", userId).eq("memory_id", card.id).eq("model_name", client.modelName())
                    .eq("model_version", client.modelVersion()).eq("source_version", card.versionNo == null ? 1 : card.versionNo)
                    .eq("task_scope", "GENERAL").eq("status", "ACTIVE"));
            if (row == null) continue;
            List<Double> values = objectMapper.readValue(row.embeddingJson, new TypeReference<List<Double>>() {});
            result.put(card.id, cosine(queryVector, values));
        }
        return result;
    }

    private boolean isPostgres() {
        if (postgres != null) return postgres;
        postgres = jdbc.execute((Connection connection) -> connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("postgres"));
        return postgres;
    }

    private static double cosine(float[] a, List<Double> b) {
        if (a.length != b.size()) return 0;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b.get(i); aa += a[i] * a[i]; bb += b.get(i) * b.get(i); }
        return aa == 0 || bb == 0 ? 0 : clamp(dot / Math.sqrt(aa * bb));
    }
    /**
     * Gemini audit 2.6 (CONFIRMED/P1): serializes the vector EXACTLY as-is -- callers must have
     * already passed it through {@link #requireDimensionContract(float[])}. This used to take a
     * separate {@code dimensions} parameter and silently zero-pad (if the vector was shorter) or
     * truncate (if longer) to force-fit that width; that force-fit is exactly the anti-pattern
     * the audit calls out, so there is no longer any way to call this with a mismatched length.
     */
    private static String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) value.append(',');
            value.append(vector[i]);
        }
        return value.append(']').toString();
    }
    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
    private static String safe(String value) { return value == null ? "" : value; }
}
