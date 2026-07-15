package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class EmergenceProposalRequest {
    @NotNull @Positive public Long candidateId;
    public List<String> counterEvidence;
    @NotBlank public String expectedImpact;
    public boolean changesConstitution;
}
