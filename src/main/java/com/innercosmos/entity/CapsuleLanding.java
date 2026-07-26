package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_capsule_landing")
public class CapsuleLanding extends BaseEntity {
    public Long capsuleId;
    public Long userId;
}
