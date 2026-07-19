package com.innercosmos.service;

import java.util.List;

import static com.innercosmos.service.DataRetractionReceiptService.ACTION_CLEARED;
import static com.innercosmos.service.DataRetractionReceiptService.ACTION_ERASED;
import static com.innercosmos.service.DataRetractionReceiptService.ACTION_REVIEW_REQUIRED;
import static com.innercosmos.service.DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX;
import static com.innercosmos.service.DataRetractionReceiptService.DERIVATIVE_CAPSULE_PERSONA;
import static com.innercosmos.service.DataRetractionReceiptService.DERIVATIVE_GENOME;
import static com.innercosmos.service.DataRetractionReceiptService.DERIVATIVE_MEMORY_EMBEDDING;
import static com.innercosmos.service.DataRetractionReceiptService.SUBJECT_CAPSULE;
import static com.innercosmos.service.DataRetractionReceiptService.SUBJECT_DATA_USE_GRANT;
import static com.innercosmos.service.DataRetractionReceiptService.SUBJECT_MEMORY;

/**
 * A5 single source-&gt;derivative registry: the one canonical map of, for each data SUBJECT a user can
 * act on, which downstream DERIVATIVES are compiled from it and the default action taken when the
 * source is withdrawn or corrected. Data-rights propagation and its audit receipts fan out along
 * exactly these edges; keeping them enumerated in one place (guarded by a completeness test that fails
 * if a new derivative type is added without registering it) is what stops a newly-compiled derivative
 * from silently escaping withdrawal/correction and the audit trail.
 *
 * <p>This registry is descriptive/auditable, not the executor — the erasure itself lives in the
 * owner-scoped service paths (memory forget, capsule archive, consent-grant revoke, user correction),
 * each of which records a {@link DataRetractionReceiptService} receipt using these same constants.
 */
public final class DataDerivativeRegistry {

    /** One source-&gt;derivative edge: what is compiled from {@code subjectType}, and how it is retracted. */
    public record Edge(String subjectType, String derivativeType, String defaultAction, String description) {
    }

    private static final List<Edge> EDGES = List.of(
            new Edge(SUBJECT_MEMORY, DERIVATIVE_MEMORY_EMBEDDING, ACTION_CLEARED,
                    "Retrieval embedding of a memory card; marked stale so a superseded/forgotten memory stops being a retrieval candidate."),
            new Edge(SUBJECT_CAPSULE, DERIVATIVE_CAPSULE_MATCH_INDEX, ACTION_ERASED,
                    "Compiled matching vector of an Echo Capsule; physically erased when the capsule is archived or delisted so it stops steering discovery."),
            new Edge(SUBJECT_CAPSULE, DERIVATIVE_CAPSULE_PERSONA, ACTION_REVIEW_REQUIRED,
                    "Public persona/sandbox text of a capsule; sent to review when its authorized source material changes."),
            new Edge(SUBJECT_CAPSULE, DERIVATIVE_GENOME, ACTION_REVIEW_REQUIRED,
                    "Dynamic Genome compiled from a capsule; marked needs-review when the capsule's authorized memories change."),
            new Edge(SUBJECT_DATA_USE_GRANT, DERIVATIVE_CAPSULE_MATCH_INDEX, ACTION_ERASED,
                    "Revoking a data-use grant delists the affected capsule, erasing its matching vector."));

    private DataDerivativeRegistry() {
    }

    public static List<Edge> edges() {
        return EDGES;
    }

    /** The derivatives compiled from a given subject type. */
    public static List<Edge> edgesForSubject(String subjectType) {
        return EDGES.stream().filter(e -> e.subjectType().equals(subjectType)).toList();
    }

    /** True if the given derivative type is a registered, propagation-tracked derivative. */
    public static boolean isRegisteredDerivative(String derivativeType) {
        return EDGES.stream().anyMatch(e -> e.derivativeType().equals(derivativeType));
    }
}
