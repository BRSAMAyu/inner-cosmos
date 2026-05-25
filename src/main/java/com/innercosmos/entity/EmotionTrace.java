package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("tb_emotion_trace")
public class EmotionTrace extends BaseEntity {
    public Long userId;
    public Long sourceSessionId;
    public String emotionName;
    public Double emotionScore;
    public String weatherType;
    public String triggerScene;
    public LocalDate recordDate;
}
