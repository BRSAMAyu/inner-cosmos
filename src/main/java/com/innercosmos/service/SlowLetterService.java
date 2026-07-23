package com.innercosmos.service;

import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.SlowLetter;
import java.util.List;

public interface SlowLetterService {
    SlowLetter draft(Long userId, LetterCreateRequest request);

    /**
     * Gemini audit 1.8 (CONFIRMED/P1): owner-scoped, version-checked edit of a DRAFT letter.
     * Only the sender may edit their own draft, only while it is still DRAFT, and only if
     * {@code expectedVersion} matches the current versionNo (optimistic concurrency).
     */
    SlowLetter patchDraft(Long userId, Long id, String title, String letterBody, Integer expectedVersion);

    default SlowLetter transition(Long userId, Long id, String targetStatus) {
        return transition(userId, id, targetStatus, null);
    }

    /**
     * Gemini audit 3.3 (CONFIRMED/P1): {@code piiConfirmed} is consulted ONLY for the SENT
     * transition -- it is the sender's explicit confirmation that they still want to send a
     * letter flagged as containing soft-confirm PII (phone/email/address). It has no effect on
     * any other transition and can never override a hard-block (credentials/secrets).
     */
    SlowLetter transition(Long userId, Long id, String targetStatus, Boolean piiConfirmed);

    SlowLetter getLetter(Long userId, Long id);

    /**
     * W1 slow-letter voice reuse: on-demand MP3 synthesis of a delivered letter's body, read aloud
     * in a warm voice (reuses Aurora's own {@code TtsVoicePresets} -- no new voice catalog). Mirrors
     * the capsule-voice contract: a bounded, tap-to-play extra on top of text the recipient already
     * has. Authorization reuses the existing recipient-scoped letter-read gate (recipient-only) and
     * the existing delivered-to-recipient delivery-state gate (the same status set the inbox query
     * treats as "arrived") -- see {@link com.innercosmos.service.impl.SlowLetterServiceImpl}.
     *
     * @return raw audio bytes (MP3) of the letter body
     */
    byte[] synthesizeVoice(Long userId, Long id);

    SlowLetter replyWithLetter(Long userId, Long id, LetterCreateRequest request);

    List<SlowLetter> inbox(Long userId);

    List<SlowLetter> outbox(Long userId);

    List<LetterThread> listThreads(Long userId);

    List<SlowLetter> getThreadLetters(Long userId, Long threadId);

    void reportLetter(Long userId, Long id, String reason);

    String requestRewrite(Long userId, Long id);
}
