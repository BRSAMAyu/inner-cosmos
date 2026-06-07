package com.innercosmos.ai.self.dto;

import lombok.Data;

@Data
public class SelfReflectionVO {
    private Long id;
    private Long userId;
    private String trigger;
    private String depth;
    private String summary;
    private Long relatedStatementId;
    private String dimension;
    private String proposedBelief;
    private Double confidence;
    private String status;
    private String riskFlags;
    private String evidenceRefs;
    private String createdAt;
}