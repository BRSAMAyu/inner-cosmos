package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_claim_propagation")
public class ClaimPropagation extends BaseEntity {
    public Long userId;
    public Long correctionId;
    public Long claimId;
    public String targetKind;
    public Long targetId;
    public String status;
    public String detail;
}
