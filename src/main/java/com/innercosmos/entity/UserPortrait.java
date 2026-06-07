package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;

@TableName("tb_user_portrait")
public class UserPortrait extends BaseEntity {
    public Long userId;
    public String dim;
    public String valueJson;
    public Double score;
    public Double confidence;
    public String evidenceRefs;
    public LocalDateTime updatedAt;
}