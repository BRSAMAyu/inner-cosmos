package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_aurora_self_profile")
public class AuroraSelfProfile extends BaseEntity {
    @TableId(type = IdType.INPUT)
    public Integer id;
    public String identityJson;
    public String missionJson;
    public String voiceStyleJson;
    public String stableBoundariesJson;
    public String continuityRulesJson;
    public LocalDateTime updatedAt;
}