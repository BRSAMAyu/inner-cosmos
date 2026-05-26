package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_user_correction")
public class UserCorrection extends BaseEntity {
    public Long userId;
    public String targetType;
    public Long targetId;
    public String fieldName;
    public String oldValue;
    public String newValue;
    public String reason;
}
