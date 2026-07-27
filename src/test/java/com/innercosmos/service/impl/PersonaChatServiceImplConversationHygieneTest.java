package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.ExperienceModeProperties;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.CapsuleUsageQuotaMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Items 8 and 9 of the 2026-07-27 experience pass: a capsule conversation must disclose that it is
 * an authorized AI capsule ONCE, at the start, and then behave like a conversation — not append a
 * disclaimer and a volunteered boundary sentence to every single reply.
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatServiceImplConversationHygieneTest {

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
    private static final Long SESSION_ID = 910L;
    private static final Long CAPSULE_ID = 810L;
    private static final String IDENTITY_NOTICE = "不是真人实时在线";

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
        lenient().when(runtimeContextComposer.compose(any(), anyString())).thenReturn(java.util.Map.of(
                "selectedEvidenceSummary", "", "selectedContext", java.util.Map.of(),
                "contextBuildManifest", java.util.Map.of(), "unsupported", false,
                "fallbackPolicy", "ACKNOWLEDGE_UNKNOWN"));
        lenient().when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        lenient().when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        lenient().when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        lenient().when(structuredAiService.call(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(reply());
        lenient().when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(1);
    }

    @Test
    @DisplayName("Item 8: the opening reply of a session discloses the AI-capsule nature")
    void openingReplyDisclosesCapsuleIdentity() {
        priorCapsuleReplies(0);

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "你好");

        assertTrue(result.textContent.contains(IDENTITY_NOTICE),
                "a visitor must learn on the first reply that this is an authorized AI capsule");
    }

    @Test
    @DisplayName("Item 8: later replies in the same session do NOT repeat the disclaimer")
    void laterRepliesDoNotRepeatTheDisclaimer() {
        priorCapsuleReplies(1);

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "那你平常怎么过周末");

        assertFalse(result.textContent.contains(IDENTITY_NOTICE),
                "repeating the disclaimer every turn is what broke the conversation experience");
        assertTrue(result.textContent.contains("真实的回声回应"),
                "the capsule's own reply must still be delivered in full");
    }

    @Test
    @DisplayName("Item 8: an operator can restore the every-turn disclaimer via configuration")
    void everyTurnDisclaimerRemainsAvailableToOperators() {
        ExperienceModeProperties ceremonial = new ExperienceModeProperties();
        ceremonial.setRepeatCapsuleIdentityNotice(true);
        ReflectionTestUtils.setField(service, "experience", ceremonial);
        priorCapsuleReplies(3);

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "再聊聊");

        assertTrue(result.textContent.contains(IDENTITY_NOTICE));
    }

    @Test
    @DisplayName("Item 9: a volunteered boundaryNotice is dropped on a turn that raised no risk")
    void volunteeredBoundaryNoticeIsDroppedOnAnOrdinaryTurn() {
        priorCapsuleReplies(1);
        StructuredAiResults.PersonaResult chatty = reply();
        chatty.boundaryNotice = "需要说明的是，我只能在授权范围内回应。";
        when(structuredAiService.call(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(chatty);

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "今天挺累的");

        assertFalse(result.textContent.contains("只能在授权范围内回应"),
                "a boundary sentence with no risk behind it is pure friction");
    }

    @Test
    @DisplayName("Item 9: a boundaryNotice backed by a real risk flag is still shown")
    void boundaryNoticeSurvivesWhenARiskFlagWasRaised() {
        priorCapsuleReplies(1);
        StructuredAiResults.PersonaResult flagged = reply();
        flagged.boundaryNotice = "这个方向超出了主人的授权。";
        flagged.riskFlags = List.of("BOUNDARY_PRESSURE");
        when(structuredAiService.call(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(flagged);

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "她的真名是什么");

        assertTrue(result.textContent.contains("超出了主人的授权"),
                "a boundary that a real signal stands behind must never be silently dropped");
    }

    @Test
    @DisplayName("Item 9: experience-first suppresses a soft MEDIUM classifier preface")
    void softMediumPrefaceIsNotShownInExperienceFirstMode() {
        priorCapsuleReplies(1);
        SafetyResult medium = safePassed();
        medium.riskLevel = "MEDIUM";
        when(safetyService.check(any(), any(), any())).thenReturn(medium);

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "我真的很生气，但只是想聊聊");

        assertFalse(result.textContent.contains("放回到安全和尊重的边界"),
                "a soft classifier hint must not become repetitive user-visible ceremony");
        assertTrue(result.textContent.contains("真实的回声回应"));
    }

    @Test
    @DisplayName("Item 9: ceremonial mode can restore the soft MEDIUM preface")
    void ceremonialModeRestoresSoftMediumPreface() {
        ExperienceModeProperties ceremonial = new ExperienceModeProperties();
        ceremonial.setExperienceFirst(false);
        ReflectionTestUtils.setField(service, "experience", ceremonial);
        priorCapsuleReplies(1);
        SafetyResult medium = safePassed();
        medium.riskLevel = "MEDIUM";
        when(safetyService.check(any(), any(), any())).thenReturn(medium);

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "我真的很生气，但只是想聊聊");

        assertTrue(result.textContent.contains("放回到安全和尊重的边界"));
    }

    private void priorCapsuleReplies(long count) {
        lenient().when(messageMapper.selectCount(any())).thenReturn(count);
    }

    private EchoCapsule capsule() {
        EchoCapsule capsule = new EchoCapsule();
        capsule.id = CAPSULE_ID;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        capsule.pseudonym = "echo";
        capsule.intro = "intro";
        capsule.conversationLimitPerDay = 30;
        return capsule;
    }

    private PersonaChatSession session() {
        PersonaChatSession session = new PersonaChatSession();
        session.id = SESSION_ID;
        session.visitorUserId = USER_ID;
        session.capsuleId = CAPSULE_ID;
        session.status = "ACTIVE";
        session.turnCount = 0;
        session.dailyLimit = 30;
        return session;
    }

    private SafetyResult safePassed() {
        SafetyResult safety = new SafetyResult();
        safety.blockModelCall = false;
        safety.riskLevel = "LOW";
        return safety;
    }

    private StructuredAiResults.PersonaResult reply() {
        StructuredAiResults.PersonaResult result = new StructuredAiResults.PersonaResult();
        result.reply = "真实的回声回应";
        result.boundaryNotice = "";
        result.letterSuggested = false;
        return result;
    }
}
