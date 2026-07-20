package com.innercosmos.service;

import com.innercosmos.entity.EchoCapsule;

import java.util.List;
import java.util.Map;

/**
 * A3-capsule-matching: real embedding/vector-similarity candidate source for Echo Capsule
 * matching, mirroring the pattern already proven for memory retrieval
 * (MemoryEmbeddingIndexService: provider-backed, pgvector-scored on PostgreSQL, in-JVM cosine
 * fallback on H2, and a graceful no-op when no embedding provider is configured).
 *
 * Consent scope: candidates and the query text are the caller's responsibility. This service
 * never sees and never embeds a capsule's private fields (personaPrompt, ownerContextNote,
 * styleProfileJson, contextPreviewJson) — only whatever consent-scoped, already-public-safe text
 * the caller passes in per capsule via {@link com.innercosmos.entity.EchoCapsule#intro},
 * {@link com.innercosmos.entity.EchoCapsule#publicTags} and
 * {@link com.innercosmos.entity.EchoCapsule#pseudonym}.
 */
public interface CapsuleEmbeddingIndexService {
    /**
     * @param queryText consent-scoped text representing the viewer's current profile/trajectory
     *                   (caller must already have excluded LOCAL_ONLY/NO_EXTERNAL_PROCESSING
     *                   memory content before building this string).
     * @param candidates capsules already filtered to the viewer-safe, non-blocked, public set.
     * @return capsuleId -> cosine similarity in [-1, 1]; empty map if no embedding provider is
     *         configured, the query is blank, or candidates is empty.
     */
    Map<Long, Double> similarities(String queryText, List<EchoCapsule> candidates);

    /**
     * G5 PROFILE-PROPAGATION: physically erase every stored matching vector derived from a capsule
     * whose source consent has been withdrawn (owner forgot a source memory, archived the capsule,
     * or revoked a data-use grant so the capsule is delisted). Deleting the rows — rather than only
     * soft-flipping a status — means the derived vector stops being served the instant the owner
     * acts, keeps the {@code (capsule, model, version, content_hash)} uniqueness free for a future
     * fresh re-consent/rebuild, and leaves the audit trail to the sensitive-payload-free
     * {@link com.innercosmos.service.DataRetractionReceiptService} instead of a lingering vector.
     *
     * <p>Idempotent: a second call for an already-cleared capsule returns 0.</p>
     *
     * @return the number of embedding rows erased.
     */
    int retireForCapsule(Long capsuleId);

    RebuildResult rebuildMissing(int batchSize);

    long pendingCount();

    record RebuildResult(int selected, int indexed, int failed, long remaining) {}
}
