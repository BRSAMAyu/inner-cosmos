package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_aurora_self_statement")
public class AuroraSelfStatement extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long sessionId;
    public Long messageId;
    public String statementText;
    @TableField("trigger_type")
    public String trigger;
}
