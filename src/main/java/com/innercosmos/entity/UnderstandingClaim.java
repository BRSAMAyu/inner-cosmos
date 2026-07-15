package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_understanding_claim")
public class UnderstandingClaim extends BaseEntity {
    public Long userId;
    public String claimKey;
    public String claimType;
    public String valueJson;
    public String authorityLevel;
    public Double confidence;
    public String status;
    public String sourceType;
    public Long sourceId;
    public Integer version;
    public Long supersedesClaimId;
    public Long correctionId;
    public String evidenceRefs;
}
