package com.innercosmos.service;

public interface RhythmGuardService {
    String checkRhythm(Long userId, Long sessionId);

    boolean shouldSuggestSettle(Long userId, Long sessionId);

    String getSessionAdvice(Long userId);
}
