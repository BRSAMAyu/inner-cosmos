package com.innercosmos.service;

import com.innercosmos.dto.LiveChatInviteView;
import com.innercosmos.dto.LiveChatMessageView;
import com.innercosmos.dto.LiveChatSessionView;

import java.util.List;
import java.util.Map;

public interface LiveChatService {
    LiveChatInviteView invite(Long userId, Long targetUserId, Integer durationMinutes);

    Map<String, List<LiveChatInviteView>> listInvites(Long userId);

    LiveChatSessionView respondToInvite(Long userId, Long inviteId, String decision);

    List<LiveChatSessionView> listActiveSessions(Long userId);

    List<LiveChatMessageView> listMessages(Long userId, Long sessionId);

    LiveChatMessageView sendMessage(Long userId, Long sessionId, String messageBody);

    LiveChatSessionView endSession(Long userId, Long sessionId);
}
