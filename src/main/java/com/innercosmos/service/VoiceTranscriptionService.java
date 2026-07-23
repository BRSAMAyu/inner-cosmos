package com.innercosmos.service;

import com.innercosmos.asr.AsrResult;
import com.innercosmos.entity.VoiceTranscription;

/**
 * G2.ARCH-MODULES: gives {@code DiaryController} a service seam instead of injecting
 * {@code VoiceTranscriptionMapper} directly. Owns the owner-scope check (a transcription belongs
 * to exactly the user who created it) so it lives in one place rather than being repeated at every
 * controller call site.
 */
public interface VoiceTranscriptionService {

    /** Persists a freshly transcribed (raw, not yet edited) diary entry. */
    VoiceTranscription create(Long userId, String text, AsrResult asr, String emotionHint);

    /**
     * Returns the transcription with the given id, owned by {@code userId}.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND if it does not exist or belongs
     *         to a different user.
     */
    VoiceTranscription getOwned(Long id, Long userId);

    /**
     * Records the user's final edited diary text, marks the transcription SUBMITTED, and settles it
     * into memory.
     *
     * @throws com.innercosmos.exception.BusinessException NOT_FOUND if it does not exist or belongs
     *         to a different user.
     */
    void submitFinal(Long id, Long userId, String finalContent);
}
