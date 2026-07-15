package com.innercosmos.vo;

import java.util.List;

public record MemoryOperationPreviewVO(
        String operationType,
        List<Long> sourceMemoryIds,
        List<Impact> impacts,
        boolean irreversible,
        boolean confirmationRequired) {
    public record Impact(String target, String action, int count) {}
}
