package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_emergence_evaluation")
public class EmergenceEvaluation extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long proposalId;
    public String evaluatorVersion;
    public Boolean constitutionPass;
    public Boolean safetyPass;
    public Double fidelityScore;
    public Double qualityScore;
    public Double continuityScore;
    public String decision;
    public String reasonsJson;
    public String sandboxBefore;
    public String sandboxAfter;
}
