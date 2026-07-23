package com.innercosmos.service.impl;

import com.innercosmos.ai.tts.TtsClient;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.CapsuleUsageQuotaMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.structured.StructuredAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * W1 capsule-voice reuse: unit-coverage for {@link PersonaChatServiceImpl#synthesizeVoice}. Pins
 * every authorization gate and the Aurora-style omit-on-failure resilience without a network: the
 * {@link TtsClient} is a stub, so we assert WHICH bytes are synthesized and that a synthesis failure
 * never escapes as a raw exception (it becomes a clean {@code AI_PROVIDER_ERROR}).
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatServiceImplSynthesizeVoiceTest {

    @Mock private PersonaChatSessionMapper sessionMapper;
    @Mock private PersonaChatMessageMapper messageMapper;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private CapsuleAgent capsuleAgent;
    @Mock private SafetyService safetyService;
    @Mock private StructuredAiService structuredAiService;
    @Mock private CapsuleBoundaryMapper boundaryMapper;
    @Mock private CapsuleUsageQuotaMapper quotaMapper;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    @Mock private CapsuleGenomeService genomeService;
    @Mock private CapsuleRuntimeContextComposer runtimeContextComposer;
    @Mock private DataUseGrantService dataUseGrantService;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private BlockRelationMapper blockRelationMapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private TtsClient ttsClient;

    private static final Long VISITOR_ID = 101L;
    private static final Long SESSION_ID = 7L;
    private static final Long CAPSULE_ID = 42L;

    private PersonaChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonaChatServiceImpl(
                sessionMapper, messageMapper, capsuleMapper,
                capsuleAgent, safetyService, structuredAiService,
                boundaryMapper, quotaMapper, jdbcTemplate, authorizedMemoryRefMapper,
                genomeService, runtimeContextComposer, dataUseGrantService,
                reportRecordMapper, blockRelationMapper, transactionManager,
                userProfileMapper, Clock.systemUTC());
        ReflectionTestUtils.setField(service, "ttsClient", ttsClient);
    }

    @Test
    @DisplayName("rejects a visitor who does not own the session (reuses ownership gate)")
    void rejectsNonOwnerSession() {
        PersonaChatSession session = new PersonaChatSession();
        session.id = SESSION_ID;
        session.visitorUserId = 999L; // someone else
        session.capsuleId = CAPSULE_ID;
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(VISITOR_ID, SESSION_ID));
        assertEquals("UNAUTHORIZED", ex.code);
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("rejects when the capsule is no longer published (reuses the withdrawn gate)")
    void rejectsWithdrawnCapsule() {
        PersonaChatSession session = ownedSession();
        EchoCapsule capsule = new EchoCapsule();
        capsule.isPublic = false; // withdrawn / not public
        capsule.visibilityStatus = "WITHDRAWN";
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(VISITOR_ID, SESSION_ID));
        assertEquals("CAPSULE_WITHDRAWN", ex.code);
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("rejects when the session has no capsule reply yet to synthesize")
    void rejectsNoCapsuleReply() {
        PersonaChatSession session = ownedSession();
        EchoCapsule capsule = publishedCapsule();
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule);
        when(messageMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(VISITOR_ID, SESSION_ID));
        assertEquals("NOT_FOUND", ex.code);
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("returns AI_PROVIDER_ERROR when TTS is not configured (clean, never a 500)")
    void unavailableProviderIsCleanError() {
        PersonaChatSession session = ownedSession();
        EchoCapsule capsule = publishedCapsule();
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule);
        when(messageMapper.selectOne(any())).thenReturn(capsuleReply("你好，我听到了。"));
        when(ttsClient.available()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(VISITOR_ID, SESSION_ID));
        assertEquals("AI_PROVIDER_ERROR", ex.code);
        verify(ttsClient, never()).synthesize(anyString(), anyString());
    }

    @Test
    @DisplayName("synthesizes the last capsule reply in the distinct capsule persona voice")
    void synthesizesLastReplyInCapsuleVoice() {
        PersonaChatSession session = ownedSession();
        EchoCapsule capsule = publishedCapsule();
        String lastReplyText = "我是来自另一个人的回声，很高兴在这里与你相遇。";
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule);
        when(messageMapper.selectOne(any())).thenReturn(capsuleReply(lastReplyText));
        when(ttsClient.available()).thenReturn(true);
        byte[] audio = new byte[]{1, 2, 3, 4};
        when(ttsClient.synthesize(eq(lastReplyText), eq("capsule_calm_neutral"))).thenReturn(audio);

        byte[] result = service.synthesizeVoice(VISITOR_ID, SESSION_ID);

        assertSame(audio, result);
        // Proves the distinct capsule persona voice was selected (NOT an Aurora voice id).
        verify(ttsClient).synthesize(lastReplyText, "capsule_calm_neutral");
    }

    @Test
    @DisplayName("a synthesis failure/timeout becomes AI_PROVIDER_ERROR, never a raw exception")
    void synthesisFailureIsCleanError() {
        PersonaChatSession session = ownedSession();
        EchoCapsule capsule = publishedCapsule();
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule);
        when(messageMapper.selectOne(any())).thenReturn(capsuleReply("听这段回声。"));
        when(ttsClient.available()).thenReturn(true);
        // Mirrors the real QwenAudioTtsClient timeout path: IllegalStateException("tts synthesis timed out...").
        when(ttsClient.synthesize(anyString(), anyString()))
                .thenThrow(new IllegalStateException("tts synthesis timed out after 8000ms"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(VISITOR_ID, SESSION_ID));
        assertEquals("AI_PROVIDER_ERROR", ex.code);
    }

    // ---------- fixtures ----------

    private PersonaChatSession ownedSession() {
        PersonaChatSession session = new PersonaChatSession();
        session.id = SESSION_ID;
        session.visitorUserId = VISITOR_ID;
        session.capsuleId = CAPSULE_ID;
        return session;
    }

    private EchoCapsule publishedCapsule() {
        EchoCapsule capsule = new EchoCapsule();
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        return capsule;
    }

    private PersonaChatMessage capsuleReply(String text) {
        PersonaChatMessage message = new PersonaChatMessage();
        message.sessionId = SESSION_ID;
        message.senderType = "CAPSULE";
        message.textContent = text;
        return message;
    }
}
