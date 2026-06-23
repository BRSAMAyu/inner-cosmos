package com.innercosmos.service;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.event.DialogFinishedEvent;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.service.impl.DialogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DialogServiceTest {

    @Mock
    private DialogSessionMapper sessionMapper;

    @Mock
    private DialogMessageMapper messageMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DialogServiceImpl dialogService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long SESSION_ID = 100L;

    @BeforeEach
    void setUp() {
        dialogService = new DialogServiceImpl(sessionMapper, messageMapper, eventPublisher);
    }

    private DialogSession buildOwnedSession() {
        DialogSession session = new DialogSession();
        session.id = SESSION_ID;
        session.userId = USER_ID;
        session.title = "Test session";
        session.sessionType = "AURORA_CHAT";
        session.status = "ACTIVE";
        session.messageCount = 0;
        session.tokenEstimate = 0;
        return session;
    }

    // --- create ---

    @Test
    @DisplayName("create sets all fields correctly and inserts session")
    void create_setsAllFieldsAndInserts() {
        SessionCreateRequest request = new SessionCreateRequest();
        request.title = "My chat";
        request.sessionType = "AURORA_CHAT";
        when(sessionMapper.insert(any(DialogSession.class))).thenReturn(1);

        DialogSession result = dialogService.create(USER_ID, request);

        assertEquals(USER_ID, result.userId);
        assertEquals("My chat", result.title);
        assertEquals("AURORA_CHAT", result.sessionType);
        assertEquals("ACTIVE", result.status);
        assertEquals(0, result.messageCount);
        assertNotNull(result.startedAt);
        verify(sessionMapper).insert(any(DialogSession.class));
    }

    @Test
    @DisplayName("create uses default title when title is null")
    void create_nullTitle_usesDefault() {
        SessionCreateRequest request = new SessionCreateRequest();
        request.title = null;
        when(sessionMapper.insert(any(DialogSession.class))).thenReturn(1);

        DialogSession result = dialogService.create(USER_ID, request);

        assertNotNull(result.title);
        assertTrue(result.title.length() > 0);
    }

    @Test
    @DisplayName("create uses default title when title is blank")
    void create_blankTitle_usesDefault() {
        SessionCreateRequest request = new SessionCreateRequest();
        request.title = "   ";
        when(sessionMapper.insert(any(DialogSession.class))).thenReturn(1);

        DialogSession result = dialogService.create(USER_ID, request);

        assertNotNull(result.title);
        assertTrue(result.title.length() > 0);
    }

    @Test
    @DisplayName("create uses default sessionType when null")
    void create_nullSessionType_usesDefault() {
        SessionCreateRequest request = new SessionCreateRequest();
        request.sessionType = null;
        when(sessionMapper.insert(any(DialogSession.class))).thenReturn(1);

        DialogSession result = dialogService.create(USER_ID, request);

        assertEquals("AURORA_CHAT", result.sessionType);
    }

    // --- saveUserMessage ---

    @Test
    @DisplayName("saveUserMessage creates message and updates session")
    void saveUserMessage_createsMessageAndUpdatesSession() {
        DialogSession session = buildOwnedSession();
        ChatRequest request = new ChatRequest();
        request.sessionId = SESSION_ID;
        request.message = "Hello Aurora";

        when(messageMapper.insert(any(DialogMessage.class))).thenReturn(1);
        when(sessionMapper.update(any(), any())).thenReturn(1); // M-021: increment = atomic update

        DialogMessage result = dialogService.saveUserMessage(USER_ID, request);

        assertEquals(SESSION_ID, result.sessionId);
        assertEquals(USER_ID, result.userId);
        assertEquals("USER", result.speaker);
        assertEquals("Hello Aurora", result.textContent);
        assertEquals("TEXT", result.inputType);
        assertEquals("LOW", result.safetyLevel);
        verify(messageMapper).insert(any(DialogMessage.class));
        verify(sessionMapper).update(any(), any()); // M-021: atomic increment
    }

    @Test
    @DisplayName("saveUserMessage with audio input type preserves it")
    void saveUserMessage_audioInput_preservesInputType() {
        DialogSession session = buildOwnedSession();
        ChatRequest request = new ChatRequest();
        request.sessionId = SESSION_ID;
        request.message = "Voice message";
        request.inputType = "VOICE";
        request.audioDurationSec = 30;

        when(messageMapper.insert(any(DialogMessage.class))).thenReturn(1);
        when(sessionMapper.update(any(), any())).thenReturn(1); // M-021: increment = atomic update

        DialogMessage result = dialogService.saveUserMessage(USER_ID, request);

        assertEquals("VOICE", result.inputType);
        assertEquals(30, result.audioDurationSec);
    }

    // --- saveAuroraMessage ---

    @Test
    @DisplayName("saveAuroraMessage creates AURORA speaker message")
    void saveAuroraMessage_createsAuroraMessage() {
        DialogSession session = buildOwnedSession();
        when(messageMapper.insert(any(DialogMessage.class))).thenReturn(1);
        when(sessionMapper.update(any(), any())).thenReturn(1); // M-021: increment = atomic update

        DialogMessage result = dialogService.saveAuroraMessage(USER_ID, SESSION_ID, "Hi there!");

        assertEquals(SESSION_ID, result.sessionId);
        assertEquals(USER_ID, result.userId);
        assertEquals("AURORA", result.speaker);
        assertEquals("Hi there!", result.textContent);
        assertEquals("MOCK", result.inputType);
        assertEquals("LOW", result.safetyLevel);
        verify(messageMapper).insert(any(DialogMessage.class));
    }

    // --- finish ---

    @Test
    @DisplayName("finish updates status and publishes event")
    void finish_updatesStatusAndPublishesEvent() {
        DialogSession session = buildOwnedSession();
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);
        when(sessionMapper.update(any(), any())).thenReturn(1); // M-007: finish uses atomic conditional update

        DialogSession result = dialogService.finish(USER_ID, SESSION_ID);

        assertEquals("FINISHED", result.status);
        assertNotNull(result.endedAt);
        verify(sessionMapper).update(any(), any()); // M-007: atomic conditional update
        verify(eventPublisher).publishEvent(any(DialogFinishedEvent.class));
    }

    @Test
    @DisplayName("finish with already finished session returns without update")
    void finish_alreadyFinished_returnsWithoutUpdate() {
        DialogSession session = buildOwnedSession();
        session.status = "FINISHED";
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

        DialogSession result = dialogService.finish(USER_ID, SESSION_ID);

        assertEquals("FINISHED", result.status);
        verify(sessionMapper, never()).updateById(any(DialogSession.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("finish throws for non-existent session")
    void finish_nonExistentSession_throwsException() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dialogService.finish(USER_ID, SESSION_ID));
        assertEquals(ErrorCode.NOT_FOUND, ex.code);
    }

    @Test
    @DisplayName("finish throws for wrong user")
    void finish_wrongUser_throwsException() {
        DialogSession session = buildOwnedSession();
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dialogService.finish(OTHER_USER_ID, SESSION_ID));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.code);
    }

    // --- messages ---

    @Test
    @DisplayName("messages returns list for session")
    void messages_returnsListForSession() {
        DialogMessage msg = new DialogMessage();
        msg.sessionId = SESSION_ID;
        msg.textContent = "Hello";
        when(messageMapper.selectList(any())).thenReturn(List.of(msg));

        List<DialogMessage> result = dialogService.messages(SESSION_ID);

        assertEquals(1, result.size());
        assertEquals("Hello", result.get(0).textContent);
        verify(messageMapper).selectList(any());
    }

    @Test
    @DisplayName("messages returns empty list when no messages")
    void messages_noMessages_returnsEmptyList() {
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<DialogMessage> result = dialogService.messages(SESSION_ID);

        assertTrue(result.isEmpty());
    }

    // --- verifyOwnership ---

    @Test
    @DisplayName("verifyOwnership succeeds for correct owner")
    void verifyOwnership_correctOwner_succeeds() {
        DialogSession session = buildOwnedSession();
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

        assertDoesNotThrow(() -> dialogService.verifyOwnership(USER_ID, SESSION_ID));
    }

    @Test
    @DisplayName("verifyOwnership throws for non-existent session")
    void verifyOwnership_nonExistentSession_throwsException() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dialogService.verifyOwnership(USER_ID, SESSION_ID));
        assertEquals(ErrorCode.NOT_FOUND, ex.code);
    }

    @Test
    @DisplayName("verifyOwnership throws for wrong user")
    void verifyOwnership_wrongUser_throwsException() {
        DialogSession session = buildOwnedSession();
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> dialogService.verifyOwnership(OTHER_USER_ID, SESSION_ID));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.code);
    }
}
