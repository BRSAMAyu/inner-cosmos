package com.innercosmos.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.innercosmos.entity.BaseEntity;

/**
 * Minimal recovery envelope for a provider generation.
 *
 * <p>The user text is deliberately not copied here. {@code userMessageId} points at the
 * access-controlled dialog row, so this table contains only bounded routing metadata and no API
 * key, provider credential, prompt, model output, or hidden reasoning.</p>
 */
@TableName("tb_turn_generation_request")
public class TurnGenerationRequest extends BaseEntity {
    public Long turnId;
    public Long userId;
    public Long sessionId;
    public Long userMessageId;
    public String mode;
    public String locale;
    public String region;
    public String timezone;
    public String contextVersion;
    public Boolean foregroundAcknowledgementSent;
}
