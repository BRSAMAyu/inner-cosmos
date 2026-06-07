package com.innercosmos.ai.self.dto;

import lombok.Data;
import java.util.List;

@Data
public class CommitRequest {
    private Long candidateId;
    private Boolean userConfirmed;
    private List<String> extraEvidence;
}