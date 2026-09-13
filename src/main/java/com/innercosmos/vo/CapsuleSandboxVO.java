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
        String identityNotice,
        /**
         * CP-31 residual: true only when the reply text came from a real model call.
         * Topic-blocked, safety-blocked and provider-unavailable paths return canned
         * copy and must not wear the AI label — same name and meaning as
         * {@link com.innercosmos.vo.AuroraReplyVO#aiGenerated}.
         */
        boolean aiGenerated
) {
}
