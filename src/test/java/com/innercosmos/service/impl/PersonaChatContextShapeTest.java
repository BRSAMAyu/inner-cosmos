package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.CapsuleUsageQuotaMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.vo.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for two remediations to the per-turn AI context PersonaChatServiceImpl
 * assembles in prepareTurn():
 *
 * <p>FIX 1: "styleProfile" and "contextPreview" used to both be set to the exact same object
 * (the whole selected runtime context), serialising the largest object in the prompt twice on
 * every turn. Only "contextPreview" survives now.
 *
 * <p>FIX 2: recentHistory() used to hand the model its most recent 8 messages in
 * reverse-chronological order (orderByDesc("id") mapped straight to a list) AND included the
 * current turn's just-inserted visitor message a second time (it is already carried separately
 * as aiContext["visitorMessage"]). recentPersonaChat must now be oldest-first and must exclude
 * that just-inserted message.
 *
 * <p>Mirrors the direct-construction Mockito wiring in
 * PersonaChatServiceImplOutputLeakageGateTest / PersonaChatServiceImplBlockRevokeRecheckTest,
 * since PersonaChatServiceImpl has a large constructor with no simpler test-only wiring.
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatContextShapeTest {

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
    @Mock private com.innercosmos.mapper.ReportRecordMapper reportRecordMapper;
    @Mock private com.innercosmos.mapper.BlockRelationMapper blockRelationMapper;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private UserProfileMapper userProfileMapper;

    private PersonaChatServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 900L;
    private static final Long CAPSULE_ID = 950L;
    // The id messageMapper.insert() assigns to this turn's just-inserted VISITOR message --
    // deliberately also present (as the newest row) in the recentHistory() fixture below, so the
    // test proves the exclusion is real (Java-side filtering), not an artifact of the mock never
    // returning it in the first place.
    private static final Long CURRENT_TURN_MESSAGE_ID = 9001L;

    @BeforeEach
    void setUp() {
        service = new PersonaChatServiceImpl(
                sessionMapper, messageMapper, capsuleMapper,
                capsuleAgent, safetyService, structuredAiService,
                boundaryMapper, quotaMapper, jdbcTemplate, authorizedMemoryRefMapper,
                genomeService, runtimeContextComposer, dataUseGrantService,
                reportRecordMapper, blockRelationMapper, transactionManager,
                userProfileMapper, Clock.systemUTC());
        lenient().when(userProfileMapper.selectOne(any())).thenReturn(null);
        lenient().when(dataUseGrantService.authorizationsValid(any(), anySet())).thenReturn(true);
        lenient().when(jdbcTemplate.update(anyString(), any(Long.class))).thenReturn(1);
        lenient().when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(1);
        lenient().when(boundaryMapper.selectOne(any())).thenReturn(null);
        lenient().when(messageMapper.insert(any(PersonaChatMessage.class))).thenAnswer(inv -> {
            PersonaChatMessage m = inv.getArgument(0);
            if ("VISITOR".equals(m.senderType)) {
                m.id = CURRENT_TURN_MESSAGE_ID;
            }
            return 1;
        });
    }

    private EchoCapsule capsule() {
        EchoCapsule c = new EchoCapsule();
        c.id = CAPSULE_ID;
        c.capsuleType = "USER_CAPSULE";
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        c.pseudonym = "echo";
        c.intro = "intro";
        c.conversationLimitPerDay = 30;
        return c;
    }

    private PersonaChatSession session() {
        PersonaChatSession s = new PersonaChatSession();
        s.id = SESSION_ID;
        s.visitorUserId = USER_ID;
        s.capsuleId = CAPSULE_ID;
        s.status = "ACTIVE";
        s.turnCount = 0;
        s.dailyLimit = 30;
        return s;
    }

    private SafetyResult safePassed() {
        SafetyResult r = new SafetyResult();
        r.blockModelCall = false;
        r.riskLevel = "LOW";
        return r;
    }

    private PersonaChatMessage historyMessage(long id, String senderType, String text) {
        PersonaChatMessage m = new PersonaChatMessage();
        m.id = id;
        m.sessionId = SESSION_ID;
        m.senderType = senderType;
        m.textContent = text;
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedAiContext() {
        ArgumentCaptor<Object> contextCaptor = ArgumentCaptor.forClass(Object.class);
        verify(structuredAiService).call(any(), any(), any(), contextCaptor.capture(), any(), any());
        return (Map<String, Object>) contextCaptor.getValue();
    }

    @Test
    @DisplayName("FIX 1: the selected runtime context is carried under exactly one aiContext key, not duplicated under styleProfile and contextPreview")
    void selectedContextIsNotDuplicatedAcrossTwoKeys() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(messageMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> selectedContext = Map.of("schemaVersion", "capsule-runtime-context.v1",
                "styleProfile", Map.of("voice", "克制"));
        when(runtimeContextComposer.compose(any(), anyString())).thenReturn(Map.of(
                "selectedEvidenceSummary", "", "selectedContext", selectedContext,
                "contextBuildManifest", Map.of(), "unsupported", false,
                "fallbackPolicy", "ACKNOWLEDGE_UNKNOWN"));
        StructuredAiResults.PersonaResult reply = new StructuredAiResults.PersonaResult();
        reply.reply = "好的";
        reply.boundaryNotice = "";
        reply.letterSuggested = false;
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(reply);

        service.reply(USER_ID, SESSION_ID, "你好");

        Map<String, Object> aiContext = capturedAiContext();
        assertFalse(aiContext.containsKey("styleProfile"),
                "styleProfile must no longer be a separate top-level key -- it is nested inside contextPreview");
        assertTrue(aiContext.containsKey("contextPreview"), "contextPreview must carry the selected runtime context");
        assertSame(selectedContext, aiContext.get("contextPreview"),
                "contextPreview must be the composer's selectedContext object, unchanged");
    }

    @Test
    @DisplayName("FIX 2: recentPersonaChat is oldest-first and excludes the current turn's just-inserted visitor message")
    void recentHistoryIsChronologicalAndExcludesCurrentTurn() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(runtimeContextComposer.compose(any(), anyString())).thenReturn(Map.of(
                "selectedEvidenceSummary", "", "selectedContext", Map.of(),
                "contextBuildManifest", Map.of(), "unsupported", true,
                "fallbackPolicy", "ACKNOWLEDGE_UNKNOWN"));
        // Simulated ASC-by-id fetch. The current turn's own visitor message is deliberately
        // included here to prove the exclusion is enforced in Java, not merely assumed of the
        // mock), then three genuinely older turns.
        when(messageMapper.selectList(any())).thenReturn(List.of(
                historyMessage(100L, "CAPSULE", "第一次回声回复"),
                historyMessage(200L, "VISITOR", "第二次来访者发言"),
                historyMessage(300L, "CAPSULE", "第二次回声回复"),
                historyMessage(CURRENT_TURN_MESSAGE_ID, "VISITOR", "这是这一轮刚发的话")));
        StructuredAiResults.PersonaResult reply = new StructuredAiResults.PersonaResult();
        reply.reply = "我们继续聊聊";
        reply.boundaryNotice = "";
        reply.letterSuggested = false;
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(reply);

        service.reply(USER_ID, SESSION_ID, "这是这一轮刚发的话");

        Map<String, Object> aiContext = capturedAiContext();
        @SuppressWarnings("unchecked")
        List<String> recentPersonaChat = (List<String>) aiContext.get("recentPersonaChat");
        assertEquals(List.of(
                "CAPSULE：第一次回声回复",
                "VISITOR：第二次来访者发言",
                "CAPSULE：第二次回声回复"), recentPersonaChat,
                "history must be presented oldest-first and must exclude the current turn's visitor message");
        assertTrue(recentPersonaChat.stream().noneMatch(line -> line.contains("这是这一轮刚发的话")),
                "the current turn's visitor message must not appear in recentPersonaChat (it is already carried as visitorMessage)");
    }
}
