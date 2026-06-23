package com.innercosmos.vo;

import com.innercosmos.entity.EchoCapsule;
import java.time.LocalDateTime;

/**
 * M-004: public-safe plaza projection of an EchoCapsule. Intentionally EXCLUDES internals —
 * personaPrompt, ownerContextNote, styleProfileJson, contextPreviewJson, authorizedMemoryIds,
 * ownerUserId — so the unauthenticated plaza list never leaks capsule internals.
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
        return vo;
    }
}
