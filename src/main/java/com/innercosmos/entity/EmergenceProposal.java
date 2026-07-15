package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_emergence_proposal")
public class EmergenceProposal extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long sourceReflectionId;
    public String dimension;
    public String currentBelief;
    public String proposedBelief;
    public String evidenceRefs;
    public String counterEvidenceJson;
    public String expectedImpactJson;
    public Boolean changesConstitution;
    public Long rollbackTargetVersionId;
    public String policyVersion;
    public String status;
}
