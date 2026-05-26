package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
import com.innercosmos.service.PersonaChatService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PersonaChatServiceImpl implements PersonaChatService {
    private final PersonaChatSessionMapper sessionMapper;
    private final PersonaChatMessageMapper messageMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleAgent capsuleAgent;

    public PersonaChatServiceImpl(PersonaChatSessionMapper sessionMapper,
                                  PersonaChatMessageMapper messageMapper,
                                  EchoCapsuleMapper capsuleMapper,
                                  CapsuleAgent capsuleAgent) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.capsuleMapper = capsuleMapper;
        this.capsuleAgent = capsuleAgent;
    }

    @Override
    public PersonaChatSession create(Long userId, Long capsuleId) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null) {
            throw new BusinessException("NOT_FOUND", "共鸣体不存在");
        }
        PersonaChatSession session = new PersonaChatSession();
        session.visitorUserId = userId;
        session.capsuleId = capsuleId;
        session.status = "ACTIVE";
        session.turnCount = 0;
        session.dailyLimit = capsule.conversationLimitPerDay != null ? capsule.conversationLimitPerDay : 5;
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public PersonaChatMessage reply(Long userId, Long sessionId, String message) {
        PersonaChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("NOT_FOUND", "persona chat session not found");
        }
        if (!userId.equals(session.visitorUserId)) {
            throw new BusinessException("UNAUTHORIZED", "无权操作此会话");
        }

        PersonaChatMessage userMessage = new PersonaChatMessage();
        userMessage.sessionId = sessionId;
        userMessage.senderType = "VISITOR";
        userMessage.textContent = message;
        messageMapper.insert(userMessage);

        PersonaChatMessage capsuleMessage = new PersonaChatMessage();
        capsuleMessage.sessionId = sessionId;
        capsuleMessage.senderType = "CAPSULE";

        if (session.turnCount != null && session.turnCount >= session.dailyLimit) {
            capsuleMessage.textContent = "今天的回声已经足够深了。如果你愿意，可以把想继续说的话写成一封慢信。";
            session.status = "LETTER_GUIDED";
        } else {
            EchoCapsule capsule = capsuleMapper.selectById(session.capsuleId);
            String personaName = capsule != null && capsule.pseudonym != null ? capsule.pseudonym : "数字回声";
            String personaIntro = capsule != null && capsule.intro != null ? capsule.intro : "一个有限的共鸣体";
            String personaPrompt = capsuleAgent.buildPersonaPrompt(personaName, personaIntro);
            capsuleMessage.textContent = generateContextualReply(personaPrompt, message);
            session.turnCount = session.turnCount == null ? 1 : session.turnCount + 1;
        }
        messageMapper.insert(capsuleMessage);
        sessionMapper.updateById(session);
        return capsuleMessage;
    }

    private String generateContextualReply(String personaPrompt, String visitorMessage) {
        if (visitorMessage.contains("难过") || visitorMessage.contains("痛苦")) {
            return "我能感受到这份沉重。你的感受是真实的，我在这里陪你看清它。";
        }
        if (visitorMessage.contains("开心") || visitorMessage.contains("高兴")) {
            return "这份喜悦值得被记住。是什么让它发生的？";
        }
        if (visitorMessage.contains("迷茫") || visitorMessage.contains("不知道")) {
            return "不知道本身也是一种诚实。我们一起慢慢看，不急着下结论。";
        }
        if (visitorMessage.contains("孤独") || visitorMessage.contains("一个人")) {
            return "你现在的孤独感，不需要被否定。我在这里，虽然只是一道回声。";
        }
        return "我听见了这个片段。作为一枚有限的数字回声，我只能陪你看见其中一部分：你最想继续靠近的是什么？";
    }

    @Override
    public List<PersonaChatMessage> messages(Long sessionId) {
        QueryWrapper<PersonaChatMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByAsc("id");
        return messageMapper.selectList(query);
    }

    @Override
    public void verifyOwnership(Long userId, Long sessionId) {
        PersonaChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("NOT_FOUND", "共鸣体对话会话不存在");
        }
        if (!userId.equals(session.visitorUserId)) {
            throw new BusinessException("UNAUTHORIZED", "无权访问此会话");
        }
    }
}
