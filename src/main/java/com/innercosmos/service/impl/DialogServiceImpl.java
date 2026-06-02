package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.event.DialogFinishedEvent;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.service.DialogService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DialogServiceImpl implements DialogService {
    private final DialogSessionMapper sessionMapper;
    private final DialogMessageMapper messageMapper;
    private final ApplicationEventPublisher eventPublisher;

    public DialogServiceImpl(DialogSessionMapper sessionMapper, DialogMessageMapper messageMapper, ApplicationEventPublisher eventPublisher) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public DialogSession create(Long userId, SessionCreateRequest request) {
        DialogSession session = new DialogSession();
        session.userId = userId;
        session.title = request.title == null || request.title.isBlank() ? "今日和 Aurora 聊聊" : request.title;
        session.sessionType = request.sessionType == null ? "AURORA_CHAT" : request.sessionType;
        session.status = "ACTIVE";
        session.messageCount = 0;
        session.tokenEstimate = 0;
        session.startedAt = LocalDateTime.now();
        sessionMapper.insert(session);
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DialogMessage saveUserMessage(Long userId, ChatRequest request) {
        DialogMessage message = new DialogMessage();
        message.sessionId = request.sessionId;
        message.userId = userId;
        message.speaker = "USER";
        message.textContent = request.message;
        message.inputType = request.inputType == null ? "TEXT" : request.inputType;
        message.audioDurationSec = request.audioDurationSec;
        message.speechRate = request.speechRate;
        message.pauseCount = request.pauseCount;
        message.longPauseCount = request.longPauseCount;
        message.emotionHint = request.emotionHint;
        message.safetyLevel = "LOW";
        messageMapper.insert(message);
        int tokens = request.message == null ? 0 : request.message.length();
        increment(request.sessionId, tokens);
        return message;
    }

    @Override
    public DialogMessage saveAuroraMessage(Long userId, Long sessionId, String reply) {
        DialogMessage message = new DialogMessage();
        message.sessionId = sessionId;
        message.userId = userId;
        message.speaker = "AURORA";
        message.textContent = reply;
        message.inputType = "MOCK";
        message.safetyLevel = "LOW";
        messageMapper.insert(message);
        increment(sessionId, reply.length());
        return message;
    }

    @Override
    public DialogSession finish(Long userId, Long sessionId) {
        DialogSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "对话会话不存在");
        }
        if (!userId.equals(session.userId)) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此会话");
        }
        if ("FINISHED".equals(session.status)) {
            return session;
        }
        session.status = "FINISHED";
        session.endedAt = LocalDateTime.now();
        session.summaryAnchor = "本次对话已整理为可沉淀的记忆锚点.";
        sessionMapper.updateById(session);
        eventPublisher.publishEvent(new DialogFinishedEvent(userId, sessionId));
        return session;
    }

    @Override
    public List<DialogMessage> messages(Long sessionId) {
        QueryWrapper<DialogMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByAsc("id");
        return messageMapper.selectList(query);
    }

    @Override
    public void verifyOwnership(Long userId, Long sessionId) {
        DialogSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "对话会话不存在");
        }
        if (!userId.equals(session.userId)) {
            throw new com.innercosmos.exception.BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权访问此会话");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    private void increment(Long sessionId, int textLength) {
        DialogSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.messageCount = session.messageCount == null ? 1 : session.messageCount + 1;
            session.tokenEstimate = (session.tokenEstimate == null ? 0 : session.tokenEstimate) + Math.max(1, textLength / 2);
            sessionMapper.updateById(session);
        }
    }
}
