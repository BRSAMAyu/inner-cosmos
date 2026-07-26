package com.innercosmos.service;

import com.innercosmos.dto.ChatRequest;
import com.innercosmos.dto.DialogSessionUpdateRequest;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.vo.DialogSessionSummaryVO;
import java.util.List;

public interface DialogService {
    DialogSession create(Long userId, SessionCreateRequest request);

    List<DialogSessionSummaryVO> sessions(Long userId, Long beforeId, int limit, boolean includeArchived);

    DialogSessionSummaryVO current(Long userId);

    DialogSessionSummaryVO get(Long userId, Long sessionId);

    DialogSessionSummaryVO update(Long userId, Long sessionId, DialogSessionUpdateRequest request);

    DialogMessage saveUserMessage(Long userId, ChatRequest request);

    DialogMessage saveAuroraMessage(Long userId, Long sessionId, String reply);

    DialogSession finish(Long userId, Long sessionId);

    List<DialogMessage> messages(Long sessionId);

    List<DialogMessage> recentMessages(Long sessionId, int limit);

    void verifyOwnership(Long userId, Long sessionId);

    /**
     * The id of the most recent message in a session, or {@code null} when the session is
     * {@code null} or has no messages yet.
     */
    Long lastMessageId(Long sessionId);
}
