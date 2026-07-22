package com.innercosmos.service;

import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.vo.CapsuleQuotaVO;
import java.util.List;

public interface PersonaChatService {
    PersonaChatSession create(Long userId, Long capsuleId);

    PersonaChatMessage reply(Long userId, Long sessionId, String message);

    List<PersonaChatMessage> messages(Long sessionId);

    void verifyOwnership(Long userId, Long sessionId);

    /**
     * IC-CAP-001: returns the authoritative per-day quota state for {@code userId}
     * on {@code capsuleId}. Reads from tb_capsule_usage_quota (turnCount=0 when no
     * row exists yet today).
     */
    CapsuleQuotaVO quota(Long userId, Long capsuleId);

    /** Report a persona-chat session for review, in the moment — not only after a delivered letter. */
    void report(Long userId, Long sessionId, String reason);

    /**
     * Block the session's capsule owner (reusing the same BlockRelation the slow-letter flow
     * uses), so this visitor never matches any of that owner's capsules again, and mark the
     * session BLOCKED so the frontend stops offering it as an active conversation.
     */
    void block(Long userId, Long sessionId);
}
