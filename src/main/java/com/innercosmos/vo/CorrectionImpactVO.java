package com.innercosmos.vo;

import java.util.List;

public record CorrectionImpactVO(
        String claimKey,
        String newValue,
        List<ImpactItem> impacts,
        int affectedMemoryCount,
        int authorizedCapsuleContextCount,
        boolean confirmationRequired) {
    public record ImpactItem(String kind, Long targetId, String label, String action) {}
}
