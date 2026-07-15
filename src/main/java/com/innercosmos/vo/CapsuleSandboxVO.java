package com.innercosmos.vo;

import java.util.List;

public record CapsuleSandboxVO(
        Long capsuleId,
        Long genomeVersionId,
        Integer genomeVersionNo,
        String genomeStatus,
        String question,
        String reply,
        String boundaryNotice,
        List<String> riskFlags,
        boolean providerAvailable,
        String identityNotice
) {
}
