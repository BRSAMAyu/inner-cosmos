package com.innercosmos.vo;

import com.innercosmos.entity.PersonaChatMessage;

import java.time.LocalDateTime;

/**
 * CP-31 / closing-checklist §2-9: explicit AI-generated labeling on the visitor persona-chat
 * outflow. Same name and meaning as {@link AuroraReplyVO#aiGenerated}: a CAPSULE message is
 * the LLM reply (CapsuleAgent PERSONA_CHAT structured provider call; the mock fallback in dev
 * carries the same generation-path semantics as Aurora's mock replies), a VISITOR message is
 * the human's own text and is labeled false — never rounded up.
 */
public class PersonaChatMessageVO {
    public Long id;
    public Long sessionId;
    public String senderType;
    public String textContent;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    /** Explicit AI-generated marker; true iff senderType is a capsule (LLM) reply. */
    public Boolean aiGenerated;

    /** Mirror an entity into the labeled VO — additive over the entity's public fields. */
    public static PersonaChatMessageVO from(PersonaChatMessage message) {
        PersonaChatMessageVO vo = new PersonaChatMessageVO();
        vo.id = message.id;
        vo.sessionId = message.sessionId;
        vo.senderType = message.senderType;
        vo.textContent = message.textContent;
        vo.createdAt = message.createdAt;
        vo.updatedAt = message.updatedAt;
        vo.aiGenerated = "CAPSULE".equals(message.senderType);
        return vo;
    }
}
