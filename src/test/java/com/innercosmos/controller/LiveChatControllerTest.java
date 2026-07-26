package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.dto.LiveChatInviteRequest;
import com.innercosmos.dto.LiveChatInviteResponseRequest;
import com.innercosmos.dto.LiveChatMessageRequest;
import com.innercosmos.dto.LiveChatMessageView;
import com.innercosmos.dto.LiveChatSessionView;
import com.innercosmos.service.LiveChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveChatControllerTest {
    @Mock LiveChatService liveChatService;

    private LiveChatController controller;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        controller = new LiveChatController(liveChatService);
        session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 20L);
    }

    @Test
    void inviteUsesAuthenticatedCallerRatherThanAClientSenderId() {
        LiveChatInviteRequest request = new LiveChatInviteRequest();
        request.targetUserId = 30L;
        request.durationMinutes = 10;

        controller.invite(request, session);

        verify(liveChatService).invite(20L, 30L, 10);
    }

    @Test
    void responsePassesOnlyTheInviteIdAndDecision() {
        LiveChatInviteResponseRequest request = new LiveChatInviteResponseRequest();
        request.decision = "accept";

        controller.respond(8L, request, session);

        verify(liveChatService).respondToInvite(20L, 8L, "accept");
    }

    @Test
    void activeSessionsAndInvitesAreCallerScoped() {
        when(liveChatService.listInvites(20L)).thenReturn(Map.of("incoming", List.of(), "outgoing", List.of()));
        when(liveChatService.listActiveSessions(20L)).thenReturn(List.of());

        controller.invites(session);
        controller.activeSessions(session);

        verify(liveChatService).listInvites(20L);
        verify(liveChatService).listActiveSessions(20L);
    }

    @Test
    void messageSenderIsAlwaysTheAuthenticatedCaller() {
        LiveChatMessageRequest request = new LiveChatMessageRequest();
        request.messageBody = "现在聊聊";
        LiveChatMessageView message = new LiveChatMessageView(
                4L, 9L, 20L, "我", "现在聊聊", OffsetDateTime.now());
        when(liveChatService.sendMessage(20L, 9L, "现在聊聊")).thenReturn(message);

        var result = controller.sendMessage(9L, request, session).data;

        assertEquals(20L, result.senderUserId());
        verify(liveChatService).sendMessage(20L, 9L, "现在聊聊");
    }

    @Test
    void readAndEndRemainParticipantScopedInTheService() {
        when(liveChatService.listMessages(20L, 9L)).thenReturn(List.of());
        LiveChatSessionView ended = new LiveChatSessionView(
                9L, 8L, 20L, "我", 30L, "朋友", 10,
                "ENDED", OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), 20L);
        when(liveChatService.endSession(20L, 9L)).thenReturn(ended);

        controller.messages(9L, session);
        var result = controller.end(9L, session).data;

        assertEquals("ENDED", result.status());
        verify(liveChatService).listMessages(20L, 9L);
        verify(liveChatService).endSession(20L, 9L);
    }
}
