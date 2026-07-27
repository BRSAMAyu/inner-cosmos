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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    /**
     * Consent scopes whose content must never be transmitted to a third-party embedding provider.
     * SIMULATOR_AUTHORIZED is a purpose-limited grant ("simulator testing only") that
     * MemoryRetrievalServiceImpl and CapsuleServiceImpl already treat as provider-forbidden; the
     * indexing path used to omit it and hand that content to the provider anyway.
     */
    private static final Set<String> PROVIDER_FORBIDDEN_CONSENT =
            Set.of("LOCAL_ONLY", "NO_EXTERNAL_PROCESSING", "SIMULATOR_AUTHORIZED");
    /** Bounded, short-lived memo of query text -> vector. Cuts a provider round-trip off repeats. */
    private static final int QUERY_CACHE_ENTRIES = 256;
    private static final long QUERY_CACHE_TTL_NANOS = TimeUnit.MINUTES.toNanos(5);

    private final MemoryEmbeddingClient client;
    private final MemoryEmbeddingMapper mapper;
    private final MemoryCardMapper memoryMapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private volatile Boolean postgres;
    private final Map<String, CachedVector> queryVectorCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedVector> eldest) {
                    return size() > QUERY_CACHE_ENTRIES;
                }
            });

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
                    .filter(card -> !providerForbidden(card))
                    .toList();
            if (providerEligible.isEmpty()) return Map.of();
            float[] queryVector = queryVector(query);
            return isPostgres() ? postgresScores(userId, queryVector, providerEligible)
                    : localScores(userId, queryVector, providerEligible);
        } catch (Exception failure) {
            // Embeddings only widen candidate quality. Relational privacy/status gates and the
            // deterministic lexical path remain available; never log private text or credentials.
            log.warn("Memory embedding candidate source unavailable: {}", failure.getClass().getSimpleName());
            return Map.of();
        }
    }

    @Override
    public EmbeddingIdentity identity() {
        return new EmbeddingIdentity(client.providerName(), client.modelName(), client.modelVersion());
    }

    @Override
    public RebuildResult rebuildMissing(int requestedBatchSize) {
        if (!client.available()) return new RebuildResult(0, 0, 0, 0);
        int batchSize = Math.max(1, Math.min(500, requestedBatchSize));
        List<Long> ids = mapper.selectMissingMemoryIds(client.modelName(), client.modelVersion(), batchSize);
        if (ids.isEmpty()) return new RebuildResult(0, 0, 0, 0);

        // Resolve eligibility and de-duplicate BEFORE spending any provider budget, then embed the
        // whole remaining batch in as few round-trips as the provider allows. Embedding one memory
        // per HTTP call made a cold index take minutes to warm up for no reason.
        List<MemoryCard> pending = new ArrayList<>();
        for (MemoryCard card : memoryMapper.selectBatchIds(ids)) {
            if (indexable(card) && !alreadyIndexed(card)) pending.add(card);
        }
        int indexed = 0;
        int failed = 0;
        if (!pending.isEmpty()) {
            List<float[]> vectors = embedDocuments(pending);
            for (int position = 0; position < pending.size(); position++) {
                MemoryCard card = pending.get(position);
                try {
                    float[] vector = vectors.get(position);
                    if (vector == null) { failed++; continue; }
                    if (store(card, vector)) indexed++;
                } catch (Exception failure) {
                    failed++;
                    // Never log memory content, provider response bodies, or credentials.
                    log.warn("Memory embedding rebuild failed for memory {}: {}", card.id,
                            failure.getClass().getSimpleName());
                }
            }
        }
        return new RebuildResult(ids.size(), indexed, failed, pendingCount());
    }

    @Override
    public long pendingCount() {
        if (!client.available()) return 0;
        return mapper.countMissing(client.modelName(), client.modelVersion());
    }

    private static boolean providerForbidden(MemoryCard card) {
        return PROVIDER_FORBIDDEN_CONSENT.contains(safe(card.consentScope).toUpperCase(Locale.ROOT));
    }

    private static boolean indexable(MemoryCard card) {
        return "ACTIVE".equalsIgnoreCase(safe(card.status)) && !providerForbidden(card);
    }

    private static int sourceVersion(MemoryCard card) {
        return card.versionNo == null ? 1 : card.versionNo;
    }

    private boolean alreadyIndexed(MemoryCard card) {
        return mapper.selectCount(new QueryWrapper<MemoryEmbedding>()
                .eq("user_id", card.userId).eq("memory_id", card.id).eq("model_name", client.modelName())
                .eq("model_version", client.modelVersion()).eq("source_version", sourceVersion(card))
                .eq("task_scope", "GENERAL").eq("status", "ACTIVE")) > 0;
    }

    private String documentText(MemoryCard card) {
        return String.join(" ", safe(card.title), safe(card.summary),
                safe(card.keywordTags), safe(card.peopleTags));
    }

    /**
     * One provider call per chunk when the client supports batching, with a per-memory fallback so
     * a client that cannot batch (or returns a malformed batch) still indexes correctly rather than
     * pairing memories with each other's vectors.
     */
    private List<float[]> embedDocuments(List<MemoryCard> cards) {
        List<String> texts = cards.stream().map(this::documentText).toList();
        try {
            List<float[]> batched = client.embedBatch(texts);
            if (batched != null && batched.size() == texts.size()) return batched;
        } catch (Exception batchFailure) {
            log.warn("Batch embedding unavailable, falling back to per-memory calls: {}",
                    batchFailure.getClass().getSimpleName());
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            try {
                vectors.add(client.embed(text));
            } catch (Exception failure) {
                vectors.add(null);
                log.warn("Memory embedding call failed: {}", failure.getClass().getSimpleName());
            }
        }
        return vectors;
    }

    private float[] queryVector(String query) {
        String key = client.modelName() + ' ' + client.modelVersion() + ' ' + query;
        long now = System.nanoTime();
        CachedVector cached = queryVectorCache.get(key);
        if (cached != null && now - cached.storedAtNanos() < QUERY_CACHE_TTL_NANOS) return cached.vector();
        float[] vector = client.embed(query);
        queryVectorCache.put(key, new CachedVector(vector, now));
        return vector;
    }

    private boolean store(MemoryCard card, float[] vector) throws Exception {
        MemoryEmbedding row = new MemoryEmbedding();
        row.userId = card.userId; row.memoryId = card.id; row.modelName = client.modelName();
        row.modelVersion = client.modelVersion(); row.sourceVersion = sourceVersion(card);
        row.taskScope = "GENERAL";
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

    private Map<Long, Double> postgresScores(Long userId, float[] queryVector, List<MemoryCard> candidates) {
        // Gemini audit 2.6: the query vector is compared against the SAME fixed-width column, so
        // it is held to the same fail-fast contract -- never silently padded/truncated to compare
        // apples to oranges against stored 1536-dimension vectors.
        requireDimensionContract(queryVector);
        List<Long> ids = candidates.stream().map(card -> card.id).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String vector = vectorLiteral(queryVector);
        // Restrict to the caller's own candidate set. Previously this returned the user's global
        // top-100 nearest rows, so a memory the caller had already excluded (consent scope, layer
        // filter, contradicted status) could occupy the top of the ranking and starve the real
        // candidates of any semantic score at all. It also lets the scan stay proportional to the
        // candidate set instead of the whole per-user index.
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids.size() + 5);
        args.add(vector); args.add(userId); args.add(client.modelName()); args.add(client.modelVersion());
        args.addAll(ids);
        args.add(vector);
        Map<Long, Double> result = new HashMap<>();
        jdbc.query("""
                SELECT e.memory_id, 1 - (e.embedding_vector <=> ?::vector) AS score
                FROM tb_memory_embedding e
                JOIN tb_memory_card c ON c.id=e.memory_id AND c.user_id=e.user_id
                  AND c.version_no=e.source_version
                WHERE e.user_id=? AND e.model_name=? AND e.model_version=?
                  AND e.task_scope='GENERAL' AND e.status='ACTIVE' AND e.embedding_vector IS NOT NULL
                  AND e.memory_id IN (%s)
                ORDER BY e.embedding_vector <=> ?::vector LIMIT 100
                """.formatted(placeholders), (org.springframework.jdbc.core.RowCallbackHandler)
                        row -> result.put(row.getLong("memory_id"), clamp(row.getDouble("score"))),
                args.toArray());
        return result;
    }

    private Map<Long, Double> localScores(Long userId, float[] queryVector, List<MemoryCard> cards) throws Exception {
        Map<Long, Double> result = new HashMap<>();
        for (MemoryCard card : cards) {
            MemoryEmbedding row = mapper.selectOne(new QueryWrapper<MemoryEmbedding>()
                    .eq("user_id", userId).eq("memory_id", card.id).eq("model_name", client.modelName())
                    .eq("model_version", client.modelVersion()).eq("source_version", sourceVersion(card))
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

    private record CachedVector(float[] vector, long storedAtNanos) {}
}
