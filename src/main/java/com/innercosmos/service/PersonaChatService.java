package com.innercosmos.service;

import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import java.util.List;

public interface PersonaChatService {
    PersonaChatSession create(Long userId, Long capsuleId);

    PersonaChatMessage reply(Long userId, Long sessionId, String message);

    List<PersonaChatMessage> messages(Long sessionId);

    void verifyOwnership(Long userId, Long sessionId);
}
