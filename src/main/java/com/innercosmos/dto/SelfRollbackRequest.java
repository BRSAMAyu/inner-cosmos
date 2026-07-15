package com.innercosmos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class SelfRollbackRequest {
    @NotNull @Positive public Long targetVersionId;
    public boolean restoreRelationship;
}
