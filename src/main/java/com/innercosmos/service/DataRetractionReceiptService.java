package com.innercosmos.service;

import com.innercosmos.entity.DataRetractionReceipt;

import java.util.List;

/**
 * A5 / G5 PROFILE-PROPAGATION: records auditable, sensitive-payload-free receipts whenever a
 * user's data-rights action invalidates a derivative of their source data (memory forget, capsule
 * archive/withdrawal, consent-grant revocation). See {@link DataRetractionReceipt}.
 */
public interface DataRetractionReceiptService {

    // Subject types — what the owner acted on.
    String SUBJECT_MEMORY = "MEMORY";
    String SUBJECT_CAPSULE = "CAPSULE";
    String SUBJECT_DATA_USE_GRANT = "DATA_USE_GRANT";

    // Derivative types — which downstream artifact the action touched.
    String DERIVATIVE_CAPSULE_MATCH_INDEX = "CAPSULE_MATCH_INDEX";
    String DERIVATIVE_CAPSULE_PERSONA = "CAPSULE_PERSONA";
    String DERIVATIVE_GENOME = "GENOME";
    String DERIVATIVE_MEMORY_EMBEDDING = "MEMORY_EMBEDDING";

    // Actions — what happened to the derivative.
    String ACTION_ERASED = "ERASED";
    String ACTION_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    String ACTION_CLEARED = "CLEARED";

    /**
     * Append an auditable receipt. Always writes a row — an idempotent re-run that affected zero
     * derivative rows still records {@code affectedCount = 0} rather than hiding that it ran.
     *
     * @return the persisted receipt (with generated id and timestamp).
     */
    DataRetractionReceipt record(Long userId, String subjectType, Long subjectId,
                                 String derivativeType, String action, int affectedCount, String reason);

    /** Owner-scoped audit trail, newest first. */
    List<DataRetractionReceipt> listForOwner(Long userId, int limit);
}
