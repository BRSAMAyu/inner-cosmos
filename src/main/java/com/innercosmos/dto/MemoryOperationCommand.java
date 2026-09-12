package com.innercosmos.dto;

import java.util.List;

/**
 * @param expectedVersion CP-21 optimistic concurrency: the version the caller read and based
 *                        this edit on. Null keeps the legacy behavior for callers that have
 *                        nothing to conflict with (settlement jobs); a value that no longer
 *                        matches the current row is a CONFLICT, never a silent overwrite of
 *                        the intervening edit.
 */
public record MemoryOperationCommand(
        String operationType,
        Long primaryMemoryId,
        List<Long> relatedMemoryIds,
        String title,
        String summary,
        List<SplitPart> splitParts,
        String reason,
        Double confidence,
        String evidenceRefs,
        Integer expectedVersion) {

    /** Legacy 9-arg shape kept for existing callers/tests — version-unpinned by design. */
    public MemoryOperationCommand(String operationType, Long primaryMemoryId, List<Long> relatedMemoryIds,
                                  String title, String summary, List<SplitPart> splitParts,
                                  String reason, Double confidence, String evidenceRefs) {
        this(operationType, primaryMemoryId, relatedMemoryIds, title, summary, splitParts,
                reason, confidence, evidenceRefs, null);
    }

    public record SplitPart(String title, String summary, String memoryLayer) {}
}
