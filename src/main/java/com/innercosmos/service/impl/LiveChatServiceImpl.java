package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.LiveChatInviteView;
import com.innercosmos.dto.LiveChatMessageView;
import com.innercosmos.dto.LiveChatSessionView;
import com.innercosmos.entity.FriendRelation;
import com.innercosmos.entity.LiveChatInvite;
import com.innercosmos.entity.LiveChatMessage;
import com.innercosmos.entity.LiveChatSession;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.FriendRelationMapper;
import com.innercosmos.mapper.LiveChatInviteMapper;
import com.innercosmos.mapper.LiveChatMessageMapper;
import com.innercosmos.mapper.LiveChatSessionMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.LiveChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LiveChatServiceImpl implements LiveChatService {
    private static final List<Integer> ALLOWED_DURATIONS = List.of(10, 15);
    private static final int INVITE_TTL_MINUTES = 5;

    private final LiveChatInviteMapper inviteMapper;
    private final LiveChatSessionMapper sessionMapper;
    private final LiveChatMessageMapper messageMapper;
    private final FriendRelationMapper friendMapper;
    private final UserMapper userMapper;
    private final Clock clock;

    @Autowired
    public LiveChatServiceImpl(LiveChatInviteMapper inviteMapper,
                               LiveChatSessionMapper sessionMapper,
                               LiveChatMessageMapper messageMapper,
                               FriendRelationMapper friendMapper,
                               UserMapper userMapper) {
        this(inviteMapper, sessionMapper, messageMapper, friendMapper, userMapper, Clock.systemUTC());
    }

    LiveChatServiceImpl(LiveChatInviteMapper inviteMapper,
                        LiveChatSessionMapper sessionMapper,
                        LiveChatMessageMapper messageMapper,
                        FriendRelationMapper friendMapper,
                        UserMapper userMapper,
                        Clock clock) {
        this.inviteMapper = inviteMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.friendMapper = friendMapper;
        this.userMapper = userMapper;
        this.clock = clock;
    }

    @Override
    public LiveChatInviteView invite(Long userId, Long targetUserId, Integer durationMinutes) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能邀请自己即时聊天");
        }
        if (!ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "durationMinutes 必须是 10 或 15");
        }
        User inviter = requireActiveHuman(userId);
        User invitee = requireActiveHuman(targetUserId);
        requireAcceptedFriendship(userId, targetUserId);
        rejectIfActiveSessionExists(userId, targetUserId);

        LiveChatInvite pending = inviteMapper.selectOne(new QueryWrapper<LiveChatInvite>()
                .eq("status", "PENDING")
                .and(q -> q.eq("inviter_user_id", userId).eq("invitee_user_id", targetUserId)
                        .or()
                        .eq("inviter_user_id", targetUserId).eq("invitee_user_id", userId))
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (pending != null && expireInviteIfNeeded(pending, now())) {
            pending = null;
        }
        if (pending != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "双方已有一条待回应的聊天邀请");
        }

        LiveChatInvite invite = new LiveChatInvite();
        invite.inviterUserId = userId;
        invite.inviteeUserId = targetUserId;
        invite.durationMinutes = durationMinutes;
        invite.status = "PENDING";
        invite.expiresAt = now().plusMinutes(INVITE_TTL_MINUTES);
        inviteMapper.insert(invite);
        return inviteView(invite, Map.of(userId, inviter, targetUserId, invitee));
    }

    @Override
    public Map<String, List<LiveChatInviteView>> listInvites(Long userId) {
        LocalDateTime now = now();
        List<LiveChatInvite> rows = inviteMapper.selectList(new QueryWrapper<LiveChatInvite>()
                .and(q -> q.eq("inviter_user_id", userId).or().eq("invitee_user_id", userId))
                .orderByDesc("id")
                .last("LIMIT 100"));
        rows.forEach(row -> expireInviteIfNeeded(row, now));
        Map<Long, User> users = usersForInvites(rows);
        List<LiveChatInviteView> incoming = rows.stream()
                .filter(row -> userId.equals(row.inviteeUserId))
                .map(row -> inviteView(row, users))
                .toList();
        List<LiveChatInviteView> outgoing = rows.stream()
                .filter(row -> userId.equals(row.inviterUserId))
                .map(row -> inviteView(row, users))
                .toList();
        Map<String, List<LiveChatInviteView>> result = new HashMap<>();
        result.put("incoming", incoming);
        result.put("outgoing", outgoing);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LiveChatSessionView respondToInvite(Long userId, Long inviteId, String decision) {
        LiveChatInvite invite = inviteMapper.selectById(inviteId);
        if (invite == null || !userId.equals(invite.inviteeUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权处理这条聊天邀请");
        }
        if (!"PENDING".equals(invite.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该聊天邀请已经被处理");
        }
        LocalDateTime now = now();
        if (expireInviteIfNeeded(invite, now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该聊天邀请已经过期");
        }

        String normalized = decision == null ? "" : decision.trim().toLowerCase();
        if (!List.of("accept", "decline").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "decision 必须是 accept 或 decline");
        }
        String nextStatus = "accept".equals(normalized) ? "ACCEPTED" : "DECLINED";
        if ("ACCEPTED".equals(nextStatus)) {
            requireAcceptedFriendship(invite.inviterUserId, invite.inviteeUserId);
            requireActiveHuman(invite.inviterUserId);
            requireActiveHuman(invite.inviteeUserId);
            rejectIfActiveSessionExists(invite.inviterUserId, invite.inviteeUserId);
        }
        int changed = inviteMapper.update(null, new UpdateWrapper<LiveChatInvite>()
                .eq("id", inviteId)
                .eq("status", "PENDING")
                .set("status", nextStatus)
                .set("responded_at", now));
        if (changed == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该聊天邀请已经被处理");
        }
        invite.status = nextStatus;
        invite.respondedAt = now;
        if ("DECLINED".equals(nextStatus)) {
            return null;
        }

        LiveChatSession session = new LiveChatSession();
        session.inviteId = invite.id;
        session.participantOneId = invite.inviterUserId;
        session.participantTwoId = invite.inviteeUserId;
        session.durationMinutes = invite.durationMinutes;
        session.status = "ACTIVE";
        session.startedAt = now;
        session.endsAt = now.plusMinutes(invite.durationMinutes);
        sessionMapper.insert(session);
        return sessionView(session, usersForSessions(List.of(session)));
    }

    @Override
    public List<LiveChatSessionView> listActiveSessions(Long userId) {
        LocalDateTime now = now();
        List<LiveChatSession> sessions = sessionMapper.selectList(new QueryWrapper<LiveChatSession>()
                .eq("status", "ACTIVE")
                .and(q -> q.eq("participant_one_id", userId).or().eq("participant_two_id", userId))
                .orderByDesc("id"));
        sessions.forEach(session -> expireSessionIfNeeded(session, now));
        List<LiveChatSession> active = sessions.stream()
                .filter(session -> "ACTIVE".equals(session.status))
                .toList();
        Map<Long, User> users = usersForSessions(active);
        return active.stream().map(session -> sessionView(session, users)).toList();
    }

    @Override
    public List<LiveChatMessageView> listMessages(Long userId, Long sessionId) {
        LiveChatSession session = requireParticipant(userId, sessionId);
        expireSessionIfNeeded(session, now());
        List<LiveChatMessage> messages = new ArrayList<>(messageMapper.selectList(
                new QueryWrapper<LiveChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByDesc("id")
                        .last("LIMIT 100")));
        Collections.reverse(messages);
        Map<Long, User> users = usersForMessages(messages);
        return messages.stream().map(message -> messageView(message, users)).toList();
    }

    @Override
    public LiveChatMessageView sendMessage(Long userId, Long sessionId, String messageBody) {
        LiveChatSession session = requireParticipant(userId, sessionId);
        expireSessionIfNeeded(session, now());
        if (!"ACTIVE".equals(session.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "这次即时聊天已经结束");
        }
        String body = messageBody == null ? "" : messageBody.trim();
        if (body.isBlank() || body.length() > 2000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息不能为空且不能超过 2000 字");
        }
        LiveChatMessage message = new LiveChatMessage();
        message.sessionId = sessionId;
        message.senderUserId = userId;
        message.messageBody = body;
        messageMapper.insert(message);
        return messageView(message, usersForMessages(List.of(message)));
    }

    @Override
    public LiveChatSessionView endSession(Long userId, Long sessionId) {
        LiveChatSession session = requireParticipant(userId, sessionId);
        LocalDateTime now = now();
        expireSessionIfNeeded(session, now);
        if (!"ACTIVE".equals(session.status)) {
            return sessionView(session, usersForSessions(List.of(session)));
        }
        int changed = sessionMapper.update(null, new UpdateWrapper<LiveChatSession>()
                .eq("id", sessionId)
                .eq("status", "ACTIVE")
                .set("status", "ENDED")
                .set("ended_at", now)
                .set("ended_by_user_id", userId));
        if (changed == 0) {
            LiveChatSession current = sessionMapper.selectById(sessionId);
            if (current == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "聊天会话不存在");
            }
            session = current;
        } else {
            session.status = "ENDED";
            session.endedAt = now;
            session.endedByUserId = userId;
        }
        return sessionView(session, usersForSessions(List.of(session)));
    }

    private User requireActiveHuman(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.status) || !"HUMAN".equals(user.accountKind)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "即时聊天只对已激活的真人账户开放");
        }
        return user;
    }

    private void requireAcceptedFriendship(Long userId, Long targetUserId) {
        Long count = friendMapper.selectCount(new QueryWrapper<FriendRelation>()
                .eq("status", "ACCEPTED")
                .and(q -> q.eq("requester_id", userId).eq("addressee_id", targetUserId)
                        .or()
                        .eq("requester_id", targetUserId).eq("addressee_id", userId)));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有双方已经接受的好友才能即时聊天");
        }
    }

    private void rejectIfActiveSessionExists(Long userId, Long targetUserId) {
        LiveChatSession existing = sessionMapper.selectOne(new QueryWrapper<LiveChatSession>()
                .eq("status", "ACTIVE")
                .and(q -> q.eq("participant_one_id", userId).eq("participant_two_id", targetUserId)
                        .or()
                        .eq("participant_one_id", targetUserId).eq("participant_two_id", userId))
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (existing == null) {
            return;
        }
        expireSessionIfNeeded(existing, now());
        if ("ACTIVE".equals(existing.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "双方已有正在进行的即时聊天");
        }
    }

    private boolean expireInviteIfNeeded(LiveChatInvite invite, LocalDateTime now) {
        if (!"PENDING".equals(invite.status) || invite.expiresAt == null || invite.expiresAt.isAfter(now)) {
            return false;
        }
        int changed = inviteMapper.update(null, new UpdateWrapper<LiveChatInvite>()
                .eq("id", invite.id)
                .eq("status", "PENDING")
                .set("status", "EXPIRED"));
        if (changed > 0) {
            invite.status = "EXPIRED";
        } else {
            LiveChatInvite current = inviteMapper.selectById(invite.id);
            if (current != null) {
                invite.status = current.status;
                invite.respondedAt = current.respondedAt;
            }
        }
        return "EXPIRED".equals(invite.status);
    }

    private boolean expireSessionIfNeeded(LiveChatSession session, LocalDateTime now) {
        if (!"ACTIVE".equals(session.status) || session.endsAt == null || session.endsAt.isAfter(now)) {
            return false;
        }
        int changed = sessionMapper.update(null, new UpdateWrapper<LiveChatSession>()
                .eq("id", session.id)
                .eq("status", "ACTIVE")
                .set("status", "EXPIRED")
                .set("ended_at", session.endsAt));
        if (changed > 0) {
            session.status = "EXPIRED";
            session.endedAt = session.endsAt;
        } else {
            LiveChatSession current = sessionMapper.selectById(session.id);
            if (current != null) {
                session.status = current.status;
                session.endedAt = current.endedAt;
                session.endedByUserId = current.endedByUserId;
            }
        }
        return "EXPIRED".equals(session.status);
    }

    private LiveChatSession requireParticipant(Long userId, Long sessionId) {
        LiveChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "聊天会话不存在");
        }
        if (!userId.equals(session.participantOneId) && !userId.equals(session.participantTwoId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权访问这次即时聊天");
        }
        return session;
    }

    private Map<Long, User> usersForInvites(List<LiveChatInvite> rows) {
        List<Long> ids = rows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.inviterUserId, row.inviteeUserId))
                .distinct().toList();
        return usersByIds(ids);
    }

    private Map<Long, User> usersForSessions(List<LiveChatSession> rows) {
        List<Long> ids = rows.stream()
                .flatMap(row -> java.util.stream.Stream.of(row.participantOneId, row.participantTwoId))
                .distinct().toList();
        return usersByIds(ids);
    }

    private Map<Long, User> usersForMessages(List<LiveChatMessage> rows) {
        return usersByIds(rows.stream().map(row -> row.senderUserId).distinct().toList());
    }

    private Map<Long, User> usersByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> result = new HashMap<>();
        userMapper.selectBatchIds(ids).forEach(user -> result.put(user.id, user));
        return result;
    }

    private LiveChatInviteView inviteView(LiveChatInvite invite, Map<Long, User> users) {
        return new LiveChatInviteView(
                invite.id,
                invite.inviterUserId,
                nickname(users.get(invite.inviterUserId)),
                invite.inviteeUserId,
                nickname(users.get(invite.inviteeUserId)),
                invite.durationMinutes,
                invite.status,
                utc(invite.expiresAt),
                utc(invite.respondedAt),
                utc(invite.createdAt));
    }

    private LiveChatSessionView sessionView(LiveChatSession session, Map<Long, User> users) {
        return new LiveChatSessionView(
                session.id,
                session.inviteId,
                session.participantOneId,
                nickname(users.get(session.participantOneId)),
                session.participantTwoId,
                nickname(users.get(session.participantTwoId)),
                session.durationMinutes,
                session.status,
                utc(session.startedAt),
                utc(session.endsAt),
                utc(session.endedAt),
                session.endedByUserId);
    }

    private LiveChatMessageView messageView(LiveChatMessage message, Map<Long, User> users) {
        return new LiveChatMessageView(
                message.id,
                message.sessionId,
                message.senderUserId,
                nickname(users.get(message.senderUserId)),
                message.messageBody,
                utc(message.createdAt));
    }

    private String nickname(User user) {
        if (user == null) {
            return "";
        }
        return user.nickname == null || user.nickname.isBlank() ? user.username : user.nickname;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
