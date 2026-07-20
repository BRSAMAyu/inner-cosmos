package com.innercosmos.vo;

import com.innercosmos.entity.DataRetractionReceipt;

import java.time.LocalDateTime;

/**
 * Owner-facing projection of a {@link DataRetractionReceipt}: the auditable "what Aurora stopped
 * using, and when" record. Carries counts and a short reason only — never any source content,
 * embedding or persona payload — so it is safe to return over the API.
 */
public record DataRetractionReceiptVO(
        Long id,
        String subjectType,
        Long subjectId,
        String derivativeType,
        String action,
        Integer affectedCount,
        String reason,
        LocalDateTime createdAt) {

    public static DataRetractionReceiptVO from(DataRetractionReceipt row) {
        return new DataRetractionReceiptVO(row.id, row.subjectType, row.subjectId,
                row.derivativeType, row.action, row.affectedCount, row.reason, row.createdAt);
    }
}
