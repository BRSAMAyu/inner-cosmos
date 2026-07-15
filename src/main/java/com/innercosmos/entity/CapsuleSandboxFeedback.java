package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_capsule_sandbox_feedback")
public class CapsuleSandboxFeedback extends BaseEntity {
    public Long capsuleId;
    public Long genomeVersionId;
    public Long ownerUserId;
    public String question;
    public String responseText;
    public String rating;
    public String ownerComment;
    public String status;
}
