package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_dialog_message")
public class DialogMessage extends BaseEntity {
    public Long sessionId;
    public Long userId;
    public String speaker;
    public String textContent;
    public String inputType;
    public Integer audioDurationSec;
    public Double speechRate;
    public Integer pauseCount;
    public Integer longPauseCount;
    public String emotionHint;
    public String safetyLevel;
}
