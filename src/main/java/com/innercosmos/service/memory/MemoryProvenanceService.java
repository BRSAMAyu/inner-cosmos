package com.innercosmos.service.memory;

import com.innercosmos.entity.MemoryOperation;
import java.util.List;

/**
 * CP-21 provenance replay: one memory's full lineage — the dialog it came from, every
 * version-changing operation, and every downstream derivative (authorized capsule
 * compilations, retrieval embeddings) with versions and timestamps, so "对话→卡片→共鸣体"
 * can be replayed and audited. Retracted memories replay as absent (CP-15 tombstone).
 */
public interface MemoryProvenanceService {

    record ProvenanceReplay(
            Long memoryId,
            Long sourceDialogSessionId,
            String currentStatus,
            Integer versionNo,
            List<OperationRow> operations,
            List<DerivativeRow> derivatives,
            String explanation) {
    }

    record OperationRow(String operationType, String fromVersion, String toVersion,
                        String actor, String createdAt) {
    }

    record DerivativeRow(String derivativeType, Long derivativeId, String status,
                         String grantVersion, String createdAt) {
    }

    ProvenanceReplay replay(Long userId, Long memoryId);
}
