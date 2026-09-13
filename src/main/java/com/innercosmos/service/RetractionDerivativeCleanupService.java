package com.innercosmos.service;

import java.util.List;
import java.util.UUID;

/**
 * CP-15 (closing-checklist §2-11): per-asset cleanup of derived copies when a durable
 * {@code data.retracted.v1} retraction event is consumed. The five inventory classes (cache /
 * object storage / export packages / push copies / provider copies) are enumerated as asset keys
 * here and inventoried — with grep evidence — in
 * {@code docs/commercialization/ledger/derivative-asset-retraction-inventory.yml}; the capsule
 * matching vector is an additional re-assertion step that REUSES
 * {@link CapsuleEmbeddingIndexService#retireForCapsule(Long)} instead of reimplementing it.
 *
 * <p>Contract of {@link #cleanForRetraction}:</p>
 * <ul>
 *   <li>Every asset runs (or is honestly recorded NOT_APPLICABLE with a reason) — one asset's
 *       failure never blocks the others (per-asset try, aggregated result).</li>
 *   <li>Every action leaves exactly one persisted result row per (outbox event, asset):
 *       SUCCESS / FAILED / NOT_APPLICABLE + reason. Failures are visible, never swallowed.</li>
 *   <li>Idempotent replay: assets whose row already settled (SUCCESS or NOT_APPLICABLE) are
 *       skipped, FAILED rows are re-attempted, and replaying the same event never deletes twice
 *       or duplicates result rows.</li>
 *   <li>If any asset ends FAILED the call throws (asset keys + receipt id only, never content),
 *       so the outbox retries the event instead of letting a retraction cleanup silently die.</li>
 * </ul>
 */
public interface RetractionDerivativeCleanupService {

    // Inventory asset classes (closing-checklist §2-11 five classes + the vector re-assert step).
    String ASSET_CACHE = "CACHE";
    String ASSET_OBJECT_STORAGE = "OBJECT_STORAGE";
    String ASSET_EXPORT_PACKAGE = "EXPORT_PACKAGE";
    String ASSET_PUSH_COPY = "PUSH_COPY";
    String ASSET_PROVIDER_COPY = "PROVIDER_COPY";
    String ASSET_CAPSULE_MATCH_VECTOR = "CAPSULE_MATCH_VECTOR";

    // Per-asset outcomes.
    String OUTCOME_SUCCESS = "SUCCESS";
    String OUTCOME_FAILED = "FAILED";
    String OUTCOME_NOT_APPLICABLE = "NOT_APPLICABLE";

    /** One consumed retraction event, sensitive-free (same fields as the v1 payload). */
    record RetractionCleanupCommand(UUID outboxEventId, long receiptId, long userId,
                                    String subjectType, long subjectId,
                                    String derivativeType, String action) {
    }

    /** One asset's cleanup outcome; {@code detail} carries the reason for every outcome. */
    record CleanupResult(String assetKey, String outcome, int affectedCount, String detail) {
        public boolean failed() {
            return OUTCOME_FAILED.equals(outcome);
        }
    }

    /**
     * Execute the per-asset cleanup for one {@code data.retracted.v1} event. Result rows persist
     * independently of the caller's transaction (a failing outer rollback must not erase the
     * evidence of what was or was not cleaned).
     *
     * @throws IllegalStateException if any asset cleanup failed (after all assets were attempted
     *                               and their rows persisted).
     */
    List<CleanupResult> cleanForRetraction(RetractionCleanupCommand command);
}
