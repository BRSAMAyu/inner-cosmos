package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.entity.CapsuleEmbedding;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.CapsuleEmbeddingMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import com.innercosmos.util.CapsulePublicTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A3-capsule-matching: real embedding/vector-similarity candidate source for Echo Capsule
 * matching. Deliberately mirrors MemoryEmbeddingIndexServiceImpl's shape (same
 * MemoryEmbeddingClient bean, pgvector cosine on PostgreSQL via {@code <=>}, in-JVM cosine
 * fallback on H2/dev, and a graceful empty-map degrade whenever the provider is unavailable or
 * anything goes wrong) so the two candidate sources share one operational story instead of two.
 *
 * Privacy-by-construction: every embedded string for a capsule comes from
 * {@link CapsulePublicTextUtils#publicSafeText(EchoCapsule)} — the same pseudonym/intro/publicTags
 * triple already rendered publicly in the plaza. This class never reads personaPrompt,
 * ownerContextNote, styleProfileJson or contextPreviewJson, and the caller (CapsuleServiceImpl) is
 * responsible for only ever passing already viewer-safe, non-blocked, public candidates and a
 * consent-scoped query text.
 */
@Service
public class CapsuleEmbeddingIndexServiceImpl implements CapsuleEmbeddingIndexService {
    private static final Logger log = LoggerFactory.getLogger(CapsuleEmbeddingIndexServiceImpl.class);
    private static final int VECTOR_DIMENSIONS = 1536;

    private final MemoryEmbeddingClient client;
    private final CapsuleEmbeddingMapper mapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private volatile Boolean postgres;

    public CapsuleEmbeddingIndexServiceImpl(MemoryEmbeddingClient client, CapsuleEmbeddingMapper mapper,
                                            JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.client = client;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Long, Double> similarities(String queryText, List<EchoCapsule> candidates) {
        if (!client.available() || queryText == null || queryText.isBlank() || candidates.isEmpty()) {
            return Map.of();
        }
        try {
            for (EchoCapsule capsule : candidates) {
                indexIfMissing(capsule);
            }
            float[] queryVector = client.embed(queryText);
            return isPostgres() ? postgresScores(queryVector, candidates) : localScores(queryVector, candidates);
        } catch (Exception failure) {
            // Embeddings only widen candidate quality; the deterministic lexical/theme path and
            // every safety/consent/block filter upstream remain the source of truth. Never log
            // capsule text or credentials.
            log.warn("Capsule embedding candidate source unavailable: {}", failure.getClass().getSimpleName());
            return Map.of();
        }
    }

    private void indexIfMissing(EchoCapsule capsule) throws Exception {
        String text = CapsulePublicTextUtils.publicSafeText(capsule);
        if (text.isBlank()) return;
        String hash = sha256(text);
        QueryWrapper<CapsuleEmbedding> query = new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id).eq("model_name", client.modelName())
                .eq("model_version", client.modelVersion()).eq("content_hash", hash).eq("status", "ACTIVE");
        if (mapper.selectCount(query) > 0) return;
        float[] vector = client.embed(text);
        CapsuleEmbedding row = new CapsuleEmbedding();
        row.capsuleId = capsule.id;
        row.modelName = client.modelName();
        row.modelVersion = client.modelVersion();
        row.contentHash = hash;
        row.dimensions = vector.length;
        row.embeddingJson = objectMapper.writeValueAsString(vector);
        row.status = "ACTIVE";
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException race) {
            return;
        }
        if (isPostgres()) {
            try {
                jdbc.update("UPDATE tb_capsule_embedding SET embedding_vector=?::vector WHERE id=?",
                        vectorLiteral(vector, VECTOR_DIMENSIONS), row.id);
            } catch (RuntimeException vectorWriteFailure) {
                mapper.deleteById(row.id);
                throw vectorWriteFailure;
            }
        }
    }

    private Map<Long, Double> postgresScores(float[] queryVector, List<EchoCapsule> candidates) {
        String vector = vectorLiteral(queryVector, VECTOR_DIMENSIONS);
        List<Long> ids = candidates.stream().map(c -> c.id).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, Double> result = new HashMap<>();
        jdbc.query("""
                SELECT e.capsule_id, MAX(1 - (e.embedding_vector <=> ?::vector)) AS score
                FROM tb_capsule_embedding e
                WHERE e.capsule_id IN (%s) AND e.model_name=? AND e.model_version=?
                  AND e.status='ACTIVE' AND e.embedding_vector IS NOT NULL
                GROUP BY e.capsule_id
                """.formatted(placeholders(ids.size())),
                (org.springframework.jdbc.core.RowCallbackHandler)
                        row -> result.put(row.getLong("capsule_id"), clamp(row.getDouble("score"))),
                buildPostgresArgs(vector, ids));
        return result;
    }

    private Object[] buildPostgresArgs(String vector, List<Long> ids) {
        Object[] args = new Object[3 + ids.size()];
        args[0] = vector;
        for (int i = 0; i < ids.size(); i++) args[1 + i] = ids.get(i);
        args[args.length - 2] = client.modelName();
        args[args.length - 1] = client.modelVersion();
        return args;
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) { if (i > 0) sb.append(','); sb.append('?'); }
        return sb.toString();
    }

    private Map<Long, Double> localScores(float[] queryVector, List<EchoCapsule> candidates) throws Exception {
        Map<Long, Double> result = new HashMap<>();
        for (EchoCapsule capsule : candidates) {
            String text = CapsulePublicTextUtils.publicSafeText(capsule);
            if (text.isBlank()) continue;
            CapsuleEmbedding row = mapper.selectOne(new QueryWrapper<CapsuleEmbedding>()
                    .eq("capsule_id", capsule.id).eq("model_name", client.modelName())
                    .eq("model_version", client.modelVersion()).eq("content_hash", sha256(text)).eq("status", "ACTIVE"));
            if (row == null) continue;
            List<Double> values = objectMapper.readValue(row.embeddingJson, new TypeReference<List<Double>>() {});
            result.put(capsule.id, cosine(queryVector, values));
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

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
