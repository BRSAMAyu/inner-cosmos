package com.innercosmos.service;

import com.innercosmos.vo.DailyRecordVO;

public interface MemorySettlementService {
    void settleSession(Long userId, Long sessionId);

    DailyRecordVO generateDailyRecord(Long userId, Long sessionId);

    void updateThemeAggregation(Long userId);

    /**
     * Settles an explicitly submitted diary into private long-term memory.
     *
     * @param sourceTranscriptionId owner-scoped VoiceTranscription source, or null only for legacy
     *                              service callers that have no persisted transcription artifact
     */
    void settleDiary(Long userId, Long sourceTranscriptionId, String diaryText);

    default void settleDiary(Long userId, String diaryText) {
        settleDiary(userId, null, diaryText);
    }
}
