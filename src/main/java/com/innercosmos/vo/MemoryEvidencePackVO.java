package com.innercosmos.vo;

import java.util.List;

public record MemoryEvidencePackVO(
        String task,
        String query,
        int tokenBudget,
        int estimatedTokens,
        List<Evidence> evidence,
        List<String> excludedStatuses) {
    public record Evidence(
            Long memoryId,
            String title,
            String summary,
            String memoryLayer,
            double score,
            List<String> contributions,
            Integer versionNo,
            String provenanceRefs) {}
}
