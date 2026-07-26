package com.innercosmos.service.impl;

import com.innercosmos.dto.LiveChatMessageView;
import com.innercosmos.dto.LiveChatSessionView;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveChatServiceImplTest {
    @Mock LiveChatInviteMapper inviteMapper;
    @Mock LiveChatSessionMapper sessionMapper;
    @Mock LiveChatMessageMapper messageMapper;
    @Mock FriendRelationMapper friendMapper;
    @Mock UserMapper userMapper;

    private LiveChatServiceImpl service;
    private User alice;
    private User bob;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-26T15:00:00Z"), ZoneOffset.UTC);
        service = new LiveChatServiceImpl(inviteMapper, sessionMapper, messageMapper, friendMapper, userMapper, clock);
        alice = human(1L, "alice", "小岚");
        bob = human(2L, "bob", "阿川");
    }

    @Test
    void acceptedHumanFriendsCanCreateAChoiceLimitedInvite() {
        stubUsersAndFriendship();
        when(inviteMapper.insert(any(LiveChatInvite.class))).thenAnswer(invocation -> {
            LiveChatInvite invite = invocation.getArgument(0);
            invite.id = 9L;
            invite.createdAt = utcNow();
            return 1;
        });

        var result = service.invite(1L, 2L, 10);

        assertEquals(9L, result.id());
        assertEquals("PENDING", result.status());
        assertEquals("小岚", result.inviterNickname());
        assertEquals("阿川", result.inviteeNickname());
        assertEquals(OffsetDateTime.parse("2026-07-26T15:05:00Z"), result.expiresAt());
    }

    @Test
    void nonFriendsCannotUseLiveChatAsAnUnsolicitedBackdoor() {
        stubUsers();
        when(friendMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.invite(1L, 2L, 10));

        assertEquals("FORBIDDEN", error.code);
        verify(inviteMapper, never()).insert(any(LiveChatInvite.class));
    }

    @Test
    void onlyTenOrFifteenMinuteSessionsAreAllowed() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.invite(1L, 2L, 30));

        assertEquals("BAD_REQUEST", error.code);
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void inviteeAcceptanceAtomicallyCreatesATimedSession() {
        LiveChatInvite invite = pendingInvite();
        when(inviteMapper.selectById(7L)).thenReturn(invite);
        when(inviteMapper.update(any(), any())).thenReturn(1);
        when(friendMapper.selectCount(any())).thenReturn(1L);
        stubUsers();
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(alice, bob));
        when(sessionMapper.insert(any(LiveChatSession.class))).thenAnswer(invocation -> {
            LiveChatSession session = invocation.getArgument(0);
            session.id = 12L;
            return 1;
        });

        LiveChatSessionView session = service.respondToInvite(2L, 7L, "accept");

        assertEquals(12L, session.id());
        assertEquals("ACTIVE", session.status());
        assertEquals(10, session.durationMinutes());
        assertEquals(session.startedAt().plusMinutes(10), session.endsAt());
        assertEquals("小岚", session.participantOneNickname());
        assertEquals("阿川", session.participantTwoNickname());
    }

    @Test
    void declineDoesNotCreateASession() {
        when(inviteMapper.selectById(7L)).thenReturn(pendingInvite());
        when(inviteMapper.update(any(), any())).thenReturn(1);

        LiveChatSessionView result = service.respondToInvite(2L, 7L, "decline");

        assertNull(result);
        verify(sessionMapper, never()).insert(any(LiveChatSession.class));
    }

    @Test
    void outsiderCannotReadSessionMessages() {
        LiveChatSession session = activeSession();
        when(sessionMapper.selectById(12L)).thenReturn(session);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listMessages(99L, 12L));

        assertEquals("UNAUTHORIZED", error.code);
        verify(messageMapper, never()).selectList(any());
    }

    @Test
    void participantCanSendATrimmedMessageWhileSessionIsActive() {
        when(sessionMapper.selectById(12L)).thenReturn(activeSession());
        when(messageMapper.insert(any(LiveChatMessage.class))).thenAnswer(invocation -> {
            LiveChatMessage message = invocation.getArgument(0);
            message.id = 31L;
            message.createdAt = utcNow();
            return 1;
        });
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(alice));

        LiveChatMessageView result = service.sendMessage(1L, 12L, "  现在刚好有空  ");

        assertEquals("现在刚好有空", result.messageBody());
        assertEquals("小岚", result.senderNickname());
        ArgumentCaptor<LiveChatMessage> inserted = ArgumentCaptor.forClass(LiveChatMessage.class);
        verify(messageMapper).insert(inserted.capture());
        assertEquals(1L, inserted.getValue().senderUserId);
    }

    @Test
    void serverExpiryStopsLateMessages() {
        LiveChatSession session = activeSession();
        session.endsAt = utcNow().minusSeconds(1);
        when(sessionMapper.selectById(12L)).thenReturn(session);
        when(sessionMapper.update(any(), any())).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendMessage(1L, 12L, "迟到的消息"));

        assertEquals("CONFLICT", error.code);
        assertEquals("EXPIRED", session.status);
        verify(messageMapper, never()).insert(any(LiveChatMessage.class));
    }

    @Test
    void messagesAreReturnedInChronologicalOrder() {
        when(sessionMapper.selectById(12L)).thenReturn(activeSession());
        LiveChatMessage later = message(32L, 2L, "第二句");
        LiveChatMessage earlier = message(31L, 1L, "第一句");
        when(messageMapper.selectList(any())).thenReturn(List.of(later, earlier));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(alice, bob));

        List<LiveChatMessageView> result = service.listMessages(1L, 12L);

        assertEquals(List.of("第一句", "第二句"),
                result.stream().map(LiveChatMessageView::messageBody).toList());
    }

    @Test
    void eitherParticipantCanEndTheSession() {
        LiveChatSession session = activeSession();
        when(sessionMapper.selectById(12L)).thenReturn(session);
        when(sessionMapper.update(any(), any())).thenReturn(1);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(alice, bob));

        LiveChatSessionView result = service.endSession(2L, 12L);

        assertEquals("ENDED", result.status());
        assertEquals(2L, result.endedByUserId());
        assertEquals(OffsetDateTime.parse("2026-07-26T15:00:00Z"), result.endedAt());
    }

    private void stubUsersAndFriendship() {
        stubUsers();
        when(friendMapper.selectCount(any())).thenReturn(1L);
    }

    private void stubUsers() {
        when(userMapper.selectById(1L)).thenReturn(alice);
        when(userMapper.selectById(2L)).thenReturn(bob);
    }

    private User human(Long id, String username, String nickname) {
        User user = new User();
        user.id = id;
        user.username = username;
        user.nickname = nickname;
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        return user;
    }

    private LiveChatInvite pendingInvite() {
        LiveChatInvite invite = new LiveChatInvite();
        invite.id = 7L;
        invite.inviterUserId = 1L;
        invite.inviteeUserId = 2L;
        invite.durationMinutes = 10;
        invite.status = "PENDING";
        invite.expiresAt = utcNow().plusMinutes(4);
        return invite;
    }

    private LiveChatSession activeSession() {
        LiveChatSession session = new LiveChatSession();
        session.id = 12L;
        session.inviteId = 7L;
        session.participantOneId = 1L;
        session.participantTwoId = 2L;
        session.durationMinutes = 10;
        session.status = "ACTIVE";
        session.startedAt = utcNow().minusMinutes(1);
        session.endsAt = utcNow().plusMinutes(9);
        return session;
    }

    private LiveChatMessage message(Long id, Long senderId, String body) {
        LiveChatMessage message = new LiveChatMessage();
        message.id = id;
        message.sessionId = 12L;
        message.senderUserId = senderId;
        message.messageBody = body;
        message.createdAt = utcNow();
        return message;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
