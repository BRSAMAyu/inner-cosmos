package com.innercosmos.conversation.service;

import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.vo.AuroraReplyVO;
import java.util.List;

public interface ConversationChoreographyService {
    TurnTimelineVO recordCompletedTurn(Long userId, Long sessionId, Long userMessageId,
                                       AuroraReplyVO reply, List<DialogMessage> persistedBubbles);

    TurnTimelineVO timeline(Long userId, Long turnId);
}
