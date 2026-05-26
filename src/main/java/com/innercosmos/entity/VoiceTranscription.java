package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_voice_transcription")
public class VoiceTranscription extends BaseEntity {
    public Long userId;
    public Long sessionId;
    public Long messageId;
    public String originalText;
    public String editedText;
    public Integer audioDurationSec;
    public Double speechRate;
    public Integer pauseCount;
    public String emotionHint;
    public String status;
}
