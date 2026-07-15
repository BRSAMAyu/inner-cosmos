package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_capsule_genome_version")
public class CapsuleGenomeVersion extends BaseEntity {
    public Long capsuleId;
    public Long ownerUserId;
    public Integer versionNo;
    public Long parentVersionId;
    public String compilerVersion;
    public String status;
    public String authorizationSnapshotJson;
    public String compiledPersonaPrompt;
    public String styleProfileJson;
    public String contextPreviewJson;
    public String evaluationJson;
    public String changeReason;
}
