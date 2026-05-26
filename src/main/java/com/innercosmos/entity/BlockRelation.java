package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_block_relation")
public class BlockRelation extends BaseEntity {
    public Long blockerUserId;
    public Long blockedUserId;
    public String reason;
}
