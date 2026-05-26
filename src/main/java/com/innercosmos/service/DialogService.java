package com.innercosmos.service;

import com.innercosmos.dto.ChatRequest;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import java.util.List;

public interface DialogService {
    DialogSession create(Long userId, SessionCreateRequest request);

    DialogMessage saveUserMessage(Long userId, ChatRequest request);

    DialogMessage saveAuroraMessage(Long userId, Long sessionId, String reply);

    DialogSession finish(Long userId, Long sessionId);

    List<DialogMessage> messages(Long sessionId);

    void verifyOwnership(Long userId, Long sessionId);
}
