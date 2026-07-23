package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.entity.UserProfile;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini audit 1.7 (PARTIAL/P1): the daily-quota boundary used to be computed from a single
 * hardcoded {@code ZoneId.of("Asia/Shanghai")} constant applied to EVERY visitor, regardless of
 * their own timezone. This test proves the quota-date argument sent to the atomic quota
 * reservation SQL now reflects the VISITOR's own persisted IANA timezone (tb_user_profile.timezone)
 * -- an instant that is already "tomorrow" in Shanghai but still "today" in New York must resolve
 * to the New York calendar date for a visitor who has New York on file, not the Shanghai date.
 *
 * Before this fix: PersonaChatServiceImpl never even had a UserProfileMapper dependency, so this
 * test could not compile against the pre-fix constructor; the hardcoded constant made the
 * boundary identical for every visitor no matter what timezone they actually reported.
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatServiceImplQuotaTimezoneTest {

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

    // 2026-07-23T02:30:00Z: already 2026-07-23 10:30 in Shanghai (UTC+8), but still
    // 2026-07-22 22:30 the PREVIOUS day in New York (UTC-4 in July, EDT).
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-23T02:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private PersonaChatServiceImpl service;

    private static final Long USER_ID = 42L;
    private static final Long SESSION_ID = 900L;
    private static final Long CAPSULE_ID = 800L;

    @BeforeEach
    void setUp() {
        service = new PersonaChatServiceImpl(
                sessionMapper, messageMapper, capsuleMapper,
                capsuleAgent, safetyService, structuredAiService,
                boundaryMapper, quotaMapper, jdbcTemplate, authorizedMemoryRefMapper,
                genomeService, runtimeContextComposer, dataUseGrantService,
                reportRecordMapper, blockRelationMapper, transactionManager,
                userProfileMapper, FIXED_CLOCK);
        lenient().when(dataUseGrantService.authorizationsValid(any(), anySet())).thenReturn(true);
        lenient().when(runtimeContextComposer.compose(any(), anyString())).thenReturn(java.util.Map.of(
                "selectedEvidenceSummary", "", "selectedContext", java.util.Map.of(),
                "contextBuildManifest", java.util.Map.of(), "unsupported", true,
                "fallbackPolicy", "ACKNOWLEDGE_UNKNOWN"));
        lenient().when(jdbcTemplate.update(anyString(), any(Long.class))).thenReturn(1);
        lenient().when(structuredAiService.call(any(), any(), any(), any(), any(), any()))
                .thenReturn(aiResult("回声回应"));
        lenient().when(boundaryMapper.selectOne(any())).thenReturn(null);
    }

    private com.innercosmos.ai.structured.StructuredAiResults.PersonaResult aiResult(String reply) {
        com.innercosmos.ai.structured.StructuredAiResults.PersonaResult r =
                new com.innercosmos.ai.structured.StructuredAiResults.PersonaResult();
        r.reply = reply;
        r.boundaryNotice = "";
        r.letterSuggested = false;
        return r;
    }

    private EchoCapsule capsule() {
        EchoCapsule c = new EchoCapsule();
        c.id = CAPSULE_ID;
        c.capsuleType = "USER_CAPSULE";
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        c.pseudonym = "echo";
        c.intro = "test capsule";
        c.conversationLimitPerDay = 30;
        return c;
    }

    private PersonaChatSession activeSession() {
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

    private UserProfile profileWithZone(String timezone) {
        UserProfile p = new UserProfile();
        p.userId = USER_ID;
        p.timezone = timezone;
        return p;
    }

    @Test
    @DisplayName("1.7: a visitor with America/New_York on file gets the New York calendar date as the quota boundary, not Shanghai's")
    void reply_visitorWithNewYorkTimezone_usesNewYorkQuotaDate() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(activeSession());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(userProfileMapper.selectOne(any())).thenReturn(profileWithZone("America/New_York"));
        when(jdbcTemplate.update(
                contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(1);

        service.reply(USER_ID, SESSION_ID, "hello");

        ArgumentCaptor<Object> quotaDateCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), quotaDateCaptor.capture(), any(Object.class));

        assertEquals(LocalDate.of(2026, 7, 22), quotaDateCaptor.getValue(),
                "the quota-date boundary must reflect the visitor's OWN New York timezone (still July 22 there), " +
                        "not a hardcoded Shanghai zone (which would already be July 23)");
    }

    @Test
    @DisplayName("1.7: a visitor with no persisted timezone falls back to the documented default zone (Asia/Shanghai), not raw UTC")
    void reply_visitorWithNoProfile_fallsBackToDefaultZone() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(activeSession());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(jdbcTemplate.update(
                contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(1);

        service.reply(USER_ID, SESSION_ID, "hello");

        ArgumentCaptor<Object> quotaDateCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), quotaDateCaptor.capture(), any(Object.class));

        // 2026-07-23T02:30:00Z is already 2026-07-23 10:30 in the Asia/Shanghai fallback zone.
        assertEquals(LocalDate.of(2026, 7, 23), quotaDateCaptor.getValue());
    }
}
