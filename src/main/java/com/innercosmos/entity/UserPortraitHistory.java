package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_user_portrait_history")
public class UserPortraitHistory extends BaseEntity {
    public Long userId;
    public String dim;
    public String valueJson;
    public Double score;
    public Double confidence;
    public String evidenceRefs;
    public LocalDateTime recordedAt;
}