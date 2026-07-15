package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_aurora_self_version")
public class AuroraSelfVersion extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Integer versionNo;
    public Long parentVersionId;
    public Long rollbackTargetVersionId;
    public Long sourceProposalId;
    public String genomeJson;
    public String constitutionHash;
    public String publicNarrative;
    public String status;
    public LocalDateTime activatedAt;
    public LocalDateTime retiredAt;
}
