package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.dto.DialogSessionUpdateRequest;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.event.DialogFinishedEvent;
import com.innercosmos.mapper.ConversationTurnMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.service.DialogService;
import com.innercosmos.vo.DialogSessionSummaryVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogServiceImpl implements DialogService {
    private final DialogSessionMapper sessionMapper;
    private final DialogMessageMapper messageMapper;
    private final ConversationTurnMapper turnMapper;
    private final ApplicationEventPublisher eventPublisher;

    public DialogServiceImpl(DialogSessionMapper sessionMapper, DialogMessageMapper messageMapper,
                             ConversationTurnMapper turnMapper,
                             ApplicationEventPublisher eventPublisher) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.turnMapper = turnMapper;
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
        session.lastActivityAt = session.startedAt;
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<DialogSessionSummaryVO> sessions(Long userId, Long beforeId, int limit,
                                                  boolean includeArchived) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        QueryWrapper<DialogSession> query = new QueryWrapper<DialogSession>()
                .eq("user_id", userId);
        if (!includeArchived) query.isNull("archived_at");
        if (beforeId != null) query.lt("id", beforeId);
        query.orderByDesc("pinned_at", "last_activity_at", "id").last("LIMIT " + safeLimit);
        return summaries(sessionMapper.selectList(query));
    }

    @Override
    public DialogSessionSummaryVO current(Long userId) {
        DialogSession session = sessionMapper.selectOne(new QueryWrapper<DialogSession>()
                .eq("user_id", userId)
                .eq("status", "ACTIVE")
                .isNull("archived_at")
                .orderByDesc("last_activity_at", "id")
                .last("LIMIT 1"));
        return session == null ? null : summary(session);
    }

    @Override
    public DialogSessionSummaryVO get(Long userId, Long sessionId) {
        return summary(owned(userId, sessionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DialogSessionSummaryVO update(Long userId, Long sessionId,
                                         DialogSessionUpdateRequest request) {
        DialogSession session = owned(userId, sessionId);
        UpdateWrapper<DialogSession> update = new UpdateWrapper<DialogSession>()
                .eq("id", sessionId).eq("user_id", userId);
        boolean changed = false;
        if (request.title != null) {
            String title = request.title.strip();
            if (title.isEmpty() || title.length() > 160) {
                throw new com.innercosmos.exception.BusinessException(
                        com.innercosmos.common.ErrorCode.BAD_REQUEST,
                        "会话标题长度必须为 1 到 160 个字符");
            }
            update.set("title", title);
            session.title = title;
            changed = true;
        }
        if (request.archived != null) {
            LocalDateTime archivedAt = request.archived ? LocalDateTime.now() : null;
            update.set("archived_at", archivedAt);
            session.archivedAt = archivedAt;
            changed = true;
        }
        if (request.pinned != null) {
            LocalDateTime pinnedAt = request.pinned ? LocalDateTime.now() : null;
            update.set("pinned_at", pinnedAt);
            session.pinnedAt = pinnedAt;
            changed = true;
        }
        if (changed) sessionMapper.update(null, update);
        return summary(session);
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
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
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
        // M-007: atomic conditional UPDATE — only the winner of a concurrent finish race gets
        // rowsAffected==1 and fires the DialogFinishedEvent; the loser's UPDATE matches 0 rows
        // (status is now FINISHED), so the memory-settlement listeners never double-fire and
        // the starfield can't be corrupted by duplicate MemoryCards.
        int updated = sessionMapper.update(null, new UpdateWrapper<DialogSession>()
                .eq("id", sessionId).ne("status", "FINISHED")
                .set("status", "FINISHED")
                .set("ended_at", LocalDateTime.now())
                .set("summary_anchor", "本次对话已整理为可沉淀的记忆锚点."));
        if (updated == 1) {
            eventPublisher.publishEvent(new DialogFinishedEvent(userId, sessionId));
        }
        session.status = "FINISHED";
        session.endedAt = LocalDateTime.now();
        session.summaryAnchor = "本次对话已整理为可沉淀的记忆锚点.";
        return session;
    }

    @Override
    public List<DialogMessage> messages(Long sessionId) {
        QueryWrapper<DialogMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByAsc("id");
        return messageMapper.selectList(query);
    }

    @Override
    public List<DialogMessage> recentMessages(Long sessionId, int limit) {
        QueryWrapper<DialogMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByDesc("id").last("LIMIT " + limit);
        List<DialogMessage> result = messageMapper.selectList(query);
        java.util.Collections.reverse(result);
        return result;
    }

    @Override
    public Long lastMessageId(Long sessionId) {
        if (sessionId == null) return null;
        QueryWrapper<DialogMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByDesc("id").last("LIMIT 1");
        DialogMessage msg = messageMapper.selectOne(query);
        return msg != null ? msg.id : null;
    }

    @Override
    public void verifyOwnership(Long userId, Long sessionId) {
        owned(userId, sessionId);
    }

    private void increment(Long sessionId, int textLength) {
        // M-021: atomic counter update — a single SQL increment avoids the lost updates of the
        // old read-modify-write (which was also inert: @Transactional on a private self-call is
        // not intercepted by the Spring proxy). One statement; no transaction annotation needed.
        int add = Math.max(1, textLength / 2);
        sessionMapper.update(null, new UpdateWrapper<DialogSession>()
                .eq("id", sessionId)
                .setSql("message_count = COALESCE(message_count, 0) + 1")
                .setSql("token_estimate = COALESCE(token_estimate, 0) + " + add)
                .set("last_activity_at", LocalDateTime.now()));
    }

    private DialogSession owned(Long userId, Long sessionId) {
        DialogSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "对话会话不存在");
        }
        if (!userId.equals(session.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权访问此会话");
        }
        return session;
    }

    private DialogSessionSummaryVO summary(DialogSession session) {
        return summaries(List.of(session)).getFirst();
    }

    private List<DialogSessionSummaryVO> summaries(List<DialogSession> sessions) {
        if (sessions.isEmpty()) return List.of();
        List<Long> sessionIds = sessions.stream().map(row -> row.id).toList();
        Long userId = sessions.getFirst().userId;
        Map<Long, DialogMessage> latestMessages = new HashMap<>();
        messageMapper.selectList(new QueryWrapper<DialogMessage>()
                        .in("session_id", sessionIds).eq("user_id", userId).orderByDesc("id"))
                .forEach(message -> latestMessages.putIfAbsent(message.sessionId, message));
        Map<Long, ConversationTurn> activeTurns = new HashMap<>();
        turnMapper.selectList(new QueryWrapper<ConversationTurn>()
                        .in("session_id", sessionIds).eq("user_id", userId)
                        .in("status", List.of("GENERATING", "PLANNED", "STREAMING", "PARTIAL"))
                        .orderByDesc("id"))
                .forEach(turn -> activeTurns.putIfAbsent(turn.sessionId, turn));
        return sessions.stream()
                .map(session -> summary(session, latestMessages.get(session.id), activeTurns.get(session.id)))
                .toList();
    }

    private DialogSessionSummaryVO summary(DialogSession session, DialogMessage latest,
                                           ConversationTurn active) {
        DialogSessionSummaryVO result = new DialogSessionSummaryVO();
        result.id = session.id;
        result.title = session.title;
        result.status = session.status;
        result.messageCount = session.messageCount;
        result.startedAt = session.startedAt;
        result.lastActivityAt = session.lastActivityAt != null ? session.lastActivityAt : session.updatedAt;
        result.archivedAt = session.archivedAt;
        result.pinnedAt = session.pinnedAt;
        result.updatedAt = session.updatedAt;

        if (latest != null && latest.textContent != null) {
            String compact = latest.textContent.strip().replaceAll("\\s+", " ");
            result.preview = compact.length() <= 96 ? compact : compact.substring(0, 96) + "…";
        }
        result.activeTurnId = active == null ? null : active.id;
        return result;
    }
}
