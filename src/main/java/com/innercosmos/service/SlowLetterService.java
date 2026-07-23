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

    SlowLetter transition(Long userId, Long id, String targetStatus);

    SlowLetter getLetter(Long userId, Long id);

    SlowLetter replyWithLetter(Long userId, Long id, LetterCreateRequest request);

    List<SlowLetter> inbox(Long userId);

    List<SlowLetter> outbox(Long userId);

    List<LetterThread> listThreads(Long userId);

    List<SlowLetter> getThreadLetters(Long userId, Long threadId);

    void reportLetter(Long userId, Long id, String reason);

    String requestRewrite(Long userId, Long id);
}
