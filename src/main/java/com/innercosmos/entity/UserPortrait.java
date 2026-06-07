package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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