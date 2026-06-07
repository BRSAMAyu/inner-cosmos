package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_aurora_constitution")
public class AuroraConstitution extends BaseEntity {
    @TableId(type = IdType.INPUT)
    public Integer id = 1;
    public String identityJson;
    public String coreValuesJson;
    public String productRightsJson;
    public String hardBoundariesJson;
    public LocalDateTime updatedAt;
}
