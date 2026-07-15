package com.innercosmos.dto;

import java.util.List;

public record MemoryOperationCommand(
        String operationType,
        Long primaryMemoryId,
        List<Long> relatedMemoryIds,
        String title,
        String summary,
        List<SplitPart> splitParts,
        String reason,
        Double confidence,
        String evidenceRefs) {
    public record SplitPart(String title, String summary, String memoryLayer) {}
}
