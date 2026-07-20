package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.entity.CapsuleEmbedding;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.CapsuleEmbeddingMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final EchoCapsuleMapper capsuleMapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private volatile Boolean postgres;

    public CapsuleEmbeddingIndexServiceImpl(MemoryEmbeddingClient client,
                                            CapsuleEmbeddingMapper mapper,
                                            EchoCapsuleMapper capsuleMapper,
                                            JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.client = client;
        this.mapper = mapper;
        this.capsuleMapper = capsuleMapper;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<Long, Double> similarities(String queryText, List<EchoCapsule> candidates) {
        if (!client.available() || queryText == null || queryText.isBlank() || candidates.isEmpty()) {
            return Map.of();
        }
        try {
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

    @Override
    public int retireForCapsule(Long capsuleId) {
        if (capsuleId == null) return 0;
        // Delete every version/status row for the capsule. The pgvector column lives on the same
        // row, so a delete is an honest physical erasure of the derived vector (no orphaned vector
        // survives on PostgreSQL). The scoring path already only ever reads status='ACTIVE', but
        // relying on a soft flag would leave the withdrawn vector on disk and would collide with
        // the content-hash uniqueness constraint if the owner later re-consents and rebuilds.
        return mapper.delete(new QueryWrapper<CapsuleEmbedding>().eq("capsule_id", capsuleId));
    }

    @Override
    public RebuildResult rebuildMissing(int requestedBatchSize) {
        if (!client.available()) return new RebuildResult(0, 0, 0, 0);
        int batchSize = Math.max(1, Math.min(500, requestedBatchSize));
        List<EchoCapsule> missing = missingPublicCapsules().stream()
                .limit(batchSize)
                .toList();
        int indexed = 0;
        int failed = 0;
        for (EchoCapsule capsule : missing) {
            try {
                if (indexIfMissing(capsule)) indexed++;
            } catch (Exception failure) {
                failed++;
                log.warn("Capsule embedding rebuild failed for capsule {}: {}",
                        capsule.id, failure.getClass().getSimpleName());
            }
        }
        return new RebuildResult(missing.size(), indexed, failed, pendingCount());
    }

    @Override
    public long pendingCount() {
        if (!client.available()) return 0;
        return missingPublicCapsules().size();
    }

    private List<EchoCapsule> publicCapsules() {
        return capsuleMapper.selectList(new QueryWrapper<EchoCapsule>()
                .eq("is_public", true)
                .eq("visibility_status", "PUBLIC")
                .and(scope -> scope.eq("simulator_only", false).or().isNull("simulator_only"))
                .orderByAsc("id"));
    }

    private List<EchoCapsule> missingPublicCapsules() {
        Map<Long, Set<String>> activeHashes = new HashMap<>();
        mapper.selectList(new QueryWrapper<CapsuleEmbedding>()
                        .eq("model_name", client.modelName())
                        .eq("model_version", client.modelVersion())
                        .eq("status", "ACTIVE"))
                .forEach(row -> activeHashes
                        .computeIfAbsent(row.capsuleId, ignored -> new HashSet<>())
                        .add(row.contentHash));
        return publicCapsules().stream()
                .filter(capsule -> {
                    String text = CapsulePublicTextUtils.publicSafeText(capsule);
                    return !text.isBlank()
                            && !activeHashes.getOrDefault(capsule.id, Set.of()).contains(sha256(text));
                })
                .toList();
    }

    private boolean indexIfMissing(EchoCapsule capsule) throws Exception {
        String text = CapsulePublicTextUtils.publicSafeText(capsule);
        if (text.isBlank()) return false;
        String hash = sha256(text);
        if (mapper.selectCount(currentQuery(capsule.id, hash)) > 0) return false;
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
            return false;
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
        mapper.update(null, new UpdateWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsule.id)
                .eq("model_name", client.modelName())
                .eq("model_version", client.modelVersion())
                .eq("status", "ACTIVE")
                .ne("content_hash", hash)
                .set("status", "SUPERSEDED"));
        return true;
    }

    private QueryWrapper<CapsuleEmbedding> currentQuery(Long capsuleId, String contentHash) {
        return new QueryWrapper<CapsuleEmbedding>()
                .eq("capsule_id", capsuleId)
                .eq("model_name", client.modelName())
                .eq("model_version", client.modelVersion())
                .eq("content_hash", contentHash)
                .eq("status", "ACTIVE");
    }

    private Map<Long, Double> postgresScores(float[] queryVector, List<EchoCapsule> candidates) {
        String vector = vectorLiteral(queryVector, VECTOR_DIMENSIONS);
        List<CapsuleKey> keys = candidates.stream()
                .map(capsule -> new CapsuleKey(capsule.id,
                        sha256(CapsulePublicTextUtils.publicSafeText(capsule))))
                .toList();
        if (keys.isEmpty()) return Map.of();
        Map<Long, Double> result = new HashMap<>();
        jdbc.query("""
                SELECT e.capsule_id, 1 - (e.embedding_vector <=> ?::vector) AS score
                FROM tb_capsule_embedding e
                WHERE (%s) AND e.model_name=? AND e.model_version=?
                  AND e.status='ACTIVE' AND e.embedding_vector IS NOT NULL
                """.formatted(currentKeyPredicates(keys.size())),
                (org.springframework.jdbc.core.RowCallbackHandler)
                        row -> result.put(row.getLong("capsule_id"), clamp(row.getDouble("score"))),
                buildPostgresArgs(vector, keys));
        return result;
    }

    private Object[] buildPostgresArgs(String vector, List<CapsuleKey> keys) {
        Object[] args = new Object[3 + keys.size() * 2];
        args[0] = vector;
        int offset = 1;
        for (CapsuleKey key : keys) {
            args[offset++] = key.capsuleId();
            args[offset++] = key.contentHash();
        }
        args[args.length - 2] = client.modelName();
        args[args.length - 1] = client.modelVersion();
        return args;
    }

    private static String currentKeyPredicates(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("(e.capsule_id=? AND e.content_hash=?)");
        }
        return sb.toString();
    }

    private record CapsuleKey(Long capsuleId, String contentHash) {}

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
