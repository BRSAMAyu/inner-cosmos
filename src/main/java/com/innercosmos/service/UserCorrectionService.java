package com.innercosmos.service;

import com.innercosmos.entity.UserCorrection;
import com.innercosmos.entity.ClaimPropagation;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.vo.CorrectionConfirmationVO;
import com.innercosmos.vo.CorrectionImpactVO;

import java.util.List;

public interface UserCorrectionService {
    CorrectionImpactVO preview(Long userId, CorrectionCommand command);

    CorrectionConfirmationVO confirm(Long userId, CorrectionCommand command);

    List<UnderstandingClaim> claimHistory(Long userId, String claimKey);

    List<ClaimPropagation> propagation(Long userId, Long correctionId);

    UserCorrection recordCorrection(Long userId, String targetType, Long targetId,
                                     String fieldName, String oldValue, String newValue, String reason);

    List<UserCorrection> listCorrections(Long userId, String targetType, Long targetId);

    /**
     * RUN-005 — the N most recent corrections this user made, newest first. Feeds the
     * Aurora prompt so the model visibly adapts after a "这不太是我" correction.
     */
    List<UserCorrection> recentCorrections(Long userId, int limit);

    /**
     * RUN-006 — the N most recent corrections of a specific {@code targetType}, newest
     * first. Lets the Aurora prompt route authoritative free-form corrections
     * (AURORA_UNDERSTANDING) and soft portrait calibrations (PORTRAIT_DIM) into their
     * own blocks with different precedence. A null/blank targetType behaves like
     * {@link #recentCorrections(Long, int)}.
     */
    List<UserCorrection> recentCorrectionsByType(Long userId, String targetType, int limit);

    /**
     * M-069 — retire one of the user's own corrections without destroying audit history. Loads by id and
     * throws UNAUTHORIZED if the correction does not exist or belongs to another user,
     * so a forged id can never touch someone else's data.
     */
    void deleteCorrection(Long userId, Long id);
}
