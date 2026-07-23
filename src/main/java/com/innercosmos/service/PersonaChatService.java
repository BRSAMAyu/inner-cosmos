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
     * W1 capsule-voice reuse: synthesizes the visitor's most recent CAPSULE reply in this session
     * to MP3 audio, spoken in a persona voice DISTINCT from Aurora's inner-voice presets (see
     * {@code CapsuleVoicePresets}). On-demand: the visitor taps play on a capsule reply bubble.
     *
     * <p>Authorization reuses the same gates as {@link #reply}: the caller must own the session
     * (see {@link #verifyOwnership}) AND the capsule must still be published/public (not withdrawn
     * or needs-review) -- the exact condition {@code create()} and {@code finalizeAiTurn()} enforce.
     *
     * @return raw MP3 audio bytes of the last capsule reply
     * @throws com.innercosmos.exception.BusinessException {@code UNAUTHORIZED}/{@code NOT_FOUND}
     *         when the caller does not own the session or it has no capsule reply yet;
     *         {@code CAPSULE_WITHDRAWN} when the capsule is no longer published;
     *         {@code AI_PROVIDER_ERROR} when TTS is not configured or synthesis fails/times out
     *         (mirrors Aurora's inner-voice resilience: never breaks the chat itself).
     */
    byte[] synthesizeVoice(Long userId, Long sessionId);

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
