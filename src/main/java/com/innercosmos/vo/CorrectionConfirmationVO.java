package com.innercosmos.vo;

import com.innercosmos.entity.ClaimPropagation;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.entity.UserCorrection;

import java.util.List;

public record CorrectionConfirmationVO(
        UserCorrection correction,
        UnderstandingClaim activeClaim,
        List<ClaimPropagation> propagation) {}
