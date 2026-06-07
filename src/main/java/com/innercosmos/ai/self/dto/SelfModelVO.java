package com.innercosmos.ai.self.dto;

import lombok.Data;

@Data
public class SelfModelVO {
    private Long id;
    private Long userId;
    private String dimension;
    private String belief;
    private Double confidence;
    private String evidenceRefs;
    private String status;
    private String committedAt;
    private Integer revisionCount;
}