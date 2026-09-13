package com.innercosmos.vo;

import com.innercosmos.entity.EchoCapsule;
import java.time.LocalDateTime;
import java.util.List;

/**
 * M-004: public-safe plaza projection of an EchoCapsule. Intentionally EXCLUDES internals —
 * personaPrompt, ownerContextNote, styleProfileJson, contextPreviewJson, authorizedMemoryIds,
 * ownerUserId — so the unauthenticated plaza list never leaks capsule internals.
 *
 * <p>CP-31 / closing-checklist §2-9: this projection carries only owner-written or
 * platform-set fields (plus numeric scores), so it is explicitly labeled
 * {@code aiGenerated=false} / {@code aiGeneratedFields=[]} — an honest "checked, none
 * AI-generated" rather than an absent marker. Same field name and meaning as
 * {@link AuroraReplyVO#aiGenerated}.</p>
 */
public class EchoCapsuleVO {
    public Long id;
    public String pseudonym;
    public String intro;
    public String capsuleType;
    public String publicTags;
    public Double echoEnergy;
    public Double freshnessScore;
    public Integer conversationLimitPerDay;
    public LocalDateTime lastActivityAt;
    public Boolean allowLetterRequest;
    /** Explicit AI-generated marker; always false in this projection (no LLM fields exposed). */
    public Boolean aiGenerated = false;
    /** LLM-written field names carried by this projection; always empty here. */
    public List<String> aiGeneratedFields = List.of();

    /** Map a full entity to the public projection, dropping every internals field. */
    public static EchoCapsuleVO fromPublic(EchoCapsule c) {
        EchoCapsuleVO vo = new EchoCapsuleVO();
        vo.id = c.id;
        vo.pseudonym = c.pseudonym;
        vo.intro = c.intro;
        vo.capsuleType = c.capsuleType;
        vo.publicTags = c.publicTags;
        vo.echoEnergy = c.echoEnergy;
        vo.freshnessScore = c.freshnessScore;
        vo.conversationLimitPerDay = c.conversationLimitPerDay;
        vo.lastActivityAt = c.lastActivityAt;
        vo.allowLetterRequest = c.allowLetterRequest;
        vo.aiGenerated = false;
        vo.aiGeneratedFields = List.of();
        return vo;
    }
}
