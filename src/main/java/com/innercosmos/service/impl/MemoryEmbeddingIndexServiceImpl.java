package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
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
    private final MemoryEmbeddingClient client;
    private final MemoryEmbeddingMapper mapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private volatile Boolean postgres;

    public MemoryEmbeddingIndexServiceImpl(MemoryEmbeddingClient client, MemoryEmbeddingMapper mapper,
                                           JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.client = client; this.mapper = mapper; this.jdbc = jdbc; this.objectMapper = objectMapper;
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
            for (MemoryCard card : providerEligible) ensure(userId, card);
            return isPostgres() ? postgresScores(userId, queryVector) : localScores(userId, queryVector, providerEligible);
        } catch (Exception failure) {
            // Embeddings only widen candidate quality. Relational privacy/status gates and the
            // deterministic lexical path remain available; never log private text or credentials.
            log.warn("Memory embedding candidate source unavailable: {}", failure.getClass().getSimpleName());
            return Map.of();
        }
    }

    private void ensure(Long userId, MemoryCard card) throws Exception {
        int sourceVersion = card.versionNo == null ? 1 : card.versionNo;
        QueryWrapper<MemoryEmbedding> query = new QueryWrapper<MemoryEmbedding>()
                .eq("user_id", userId).eq("memory_id", card.id).eq("model_name", client.modelName())
                .eq("model_version", client.modelVersion()).eq("source_version", sourceVersion)
                .eq("task_scope", "GENERAL").eq("status", "ACTIVE");
        if (mapper.selectCount(query) > 0) return;
        float[] vector = client.embed(String.join(" ", safe(card.title), safe(card.summary),
                safe(card.keywordTags), safe(card.peopleTags)));
        MemoryEmbedding row = new MemoryEmbedding();
        row.userId = userId; row.memoryId = card.id; row.modelName = client.modelName();
        row.modelVersion = client.modelVersion(); row.sourceVersion = sourceVersion; row.taskScope = "GENERAL";
        row.dimensions = vector.length; row.embeddingJson = objectMapper.writeValueAsString(vector); row.status = "ACTIVE";
        try { mapper.insert(row); } catch (DuplicateKeyException race) { return; }
        if (isPostgres()) jdbc.update("UPDATE tb_memory_embedding SET embedding_vector=?::vector WHERE id=?",
                vectorLiteral(vector, 1536), row.id);
    }

    private Map<Long, Double> postgresScores(Long userId, float[] queryVector) {
        String vector = vectorLiteral(queryVector, 1536);
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
    private static String vectorLiteral(float[] vector, int dimensions) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < dimensions; i++) {
            if (i > 0) value.append(','); value.append(i < vector.length ? vector[i] : 0f);
        }
        return value.append(']').toString();
    }
    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
    private static String safe(String value) { return value == null ? "" : value; }
}
