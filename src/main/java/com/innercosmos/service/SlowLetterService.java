package com.innercosmos.service;

import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.SlowLetter;
import java.util.List;

public interface SlowLetterService {
    SlowLetter draft(Long userId, LetterCreateRequest request);

    SlowLetter transition(Long userId, Long id, String targetStatus);

    SlowLetter getLetter(Long userId, Long id);

    SlowLetter replyWithLetter(Long userId, Long id, LetterCreateRequest request);

    List<SlowLetter> inbox(Long userId);

    List<SlowLetter> outbox(Long userId);

    List<LetterThread> listThreads(Long userId);

    void reportLetter(Long userId, Long id, String reason);

    String requestRewrite(Long userId, Long id);
}
