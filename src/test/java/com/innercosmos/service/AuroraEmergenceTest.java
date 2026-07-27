package com.innercosmos.service;

import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.ai.context.AgentContextAssembler;
import com.innercosmos.ai.goodbye.GoodbyeOrchestrator;
import com.innercosmos.ai.goodbye.GoodbyeTriggerDetector;
import com.innercosmos.ai.mode.ModeRegistry;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.PortraitReflectionService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.router.ResolvedModel;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.impl.AuroraAgentServiceImpl;
import com.innercosmos.vo.AuroraReplyVO;
import com.innercosmos.vo.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VS-004 — Aurora response style emerges from portrait + relationship + state.
 * Asserts that produceReply feeds the multi-dim portrait, the relationship
 * snapshot, and the current-state signal into BOTH the built prompt AND the
 * turnContext map — and that two same-mode users with different portraits now
 * get DIFFERENT prompts (closing the "two same-mode users get identical replies"
 * gap called out in the VS-004 proposal).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuroraEmergenceTest {

    @Mock private StructuredAiService structuredAiService;
    @Mock private DialogService dialogService;
    @Mock private SafetyService safetyService;
    @Mock private MemoryService memoryService;
    @Mock private RhythmGuardService rhythmGuardService;
    @Mock private AuroraMemoryContextService memoryContextService;
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private DialogSessionMapper sessionMapper;
    @Mock private LlmConfig llmConfig;
    @Mock private AgentContextAssembler agentContextAssembler;
    @Mock private SessionModelRouter modelRouter;
    @Mock private PortraitReflectionService portraitReflection;
    @Mock private GoodbyeTriggerDetector goodbyeDetector;
    @Mock private GoodbyeOrchestrator goodbyeOrchestrator;
    @Mock private ModeRegistry modeRegistry;
    @Mock private UserPortraitService userPortraitService;
    @Mock private AgentUserRelationshipService relationshipService;

    private final Executor aiExecutor = new SyncTaskExecutor();
    private AuroraAgentServiceImpl service;

    private static final Long USER_A = 100L;
    private static final Long USER_B = 200L;
    private static final Long SESSION = 10L;

    @BeforeEach
    void setUp() {
        service = new AuroraAgentServiceImpl(structuredAiService, dialogService, safetyService,
                memoryService, rhythmGuardService, memoryContextService, userProfileMapper,
                sessionMapper, llmConfig, aiExecutor, agentContextAssembler, modelRouter,
                portraitReflection, goodbyeDetector, goodbyeOrchestrator, modeRegistry,
                null, userPortraitService, relationshipService);
    }

    private SafetyResult safe() {
        SafetyResult r = new SafetyResult();
        r.riskLevel = "LOW";
        r.riskType = "NONE";
        r.blockModelCall = false;
        return r;
    }

    private StructuredAiResults.AuroraResult okResult() {
        StructuredAiResults.AuroraResult ai = new StructuredAiResults.AuroraResult();
        ai.segments = List.of("我在。");
        ai.detectedTheme = "日常倾诉";
        return ai;
    }

    private ChatRequest request(String message) {
        ChatRequest r = new ChatRequest();
        r.sessionId = SESSION;
        r.message = message;
        r.mode = "DAILY_TALK";
        return r;
    }

    @SuppressWarnings("unchecked")
    private void stubCommonDeps() {
        when(agentContextAssembler.assemble(anyLong(), anyLong(), anyString(), anyBoolean(),
                any(), any(), any(), any(), any())).thenReturn(new AgentContext());
        when(dialogService.recentMessages(anyLong(), anyInt())).thenReturn(List.of());
        when(dialogService.messages(anyLong())).thenReturn(List.of());
        when(memoryContextService.buildContext(anyLong(), anyLong(), anyString(), anyInt(), anyInt()))
                .thenReturn(null);
        when(sessionMapper.selectById(anyLong())).thenReturn(null);
        when(rhythmGuardService.checkRhythm(anyLong(), anyLong())).thenReturn("");
        when(rhythmGuardService.shouldSuggestSettle(any(), anyLong())).thenReturn(false);
        ResolvedModel resolved = mock(ResolvedModel.class);
        when(resolved.provider()).thenReturn("MOCK");
        when(resolved.model()).thenReturn("mock");
        when(modelRouter.resolve(anyLong(), anyLong())).thenReturn(resolved);
        when(goodbyeDetector.detect(anyString())).thenReturn(GoodbyeTriggerDetector.NONE);
        when(llmConfig.activeProvider()).thenReturn("MOCK");
        when(llmConfig.activeModel()).thenReturn("mock");
        when(llmConfig.getMode()).thenReturn("mock");
        when(llmConfig.hasActiveApiKey()).thenReturn(false);
        when(llmConfig.isEffectiveFallbackAllowed()).thenReturn(true);
    }

    @Test
    @DisplayName("prompt + turnContext now contain portrait, relationship, and current-state signal (seeded user)")
    void produceReply_feedsPortraitRelationshipState() {
        when(safetyService.check(anyString(), anyLong(), anyLong(),
                any(), any(), any())).thenReturn(safe());
        stubCommonDeps();

        // Seeded portrait: two high-confidence dims.
        UserPortrait drive = portrait("INNER_DRIVE", "稳定的好奇心与长期坚持", 0.82, 0.7);
        UserPortrait energy = portrait("ENERGY_RHYTHM", "深夜更有灵感，早晨偏慢", 0.6, 0.5);
        when(userPortraitService.getAll(USER_A)).thenReturn(List.of(drive, energy));

        AgentUserRelationship rel = new AgentUserRelationship();
        rel.relationshipStage = "close_friend";
        rel.intimacyLevel = 7;
        rel.trustLevel = 6;
        rel.familiarityLevel = 8;
        rel.userDisclosureLevel = 5;
        rel.preferredAddressing = "你";
        when(relationshipService.getOrInit(USER_A)).thenReturn(rel);

        when(structuredAiService.call(anyLong(), anyString(), anyString(), any(),
                eq(StructuredAiResults.AuroraResult.class), any(), any())).thenReturn(okResult());

        AuroraReplyVO vo = service.replyRich(USER_A, request("今天有点累，撑不住了"));
        assertNotNull(vo);
        assertFalse(vo.messages.isEmpty());

        ArgumentCaptor<Map<String, Object>> ctxCap = ArgumentCaptor.forClass(Map.class);
        verify(structuredAiService).call(anyLong(), anyString(), anyString(), ctxCap.capture(),
                eq(StructuredAiResults.AuroraResult.class), any(), any());
        Map<String, Object> turnContext = ctxCap.getValue();

        // Personalization remains in one structured copy; the old prose prompt duplicate is gone.
        assertFalse(turnContext.containsKey("auroraPrompt"));
        assertFalse(turnContext.containsKey("recentAuroraMessages"));
        assertEquals("INNER_DRIVE:稳定的好奇心与长期坚持；ENERGY_RHYTHM:深夜更有灵感，早晨偏慢",
                turnContext.get("userPortrait"));
        assertTrue(String.valueOf(turnContext.get("relationship")).contains("close_friend"));
        String sig = String.valueOf(turnContext.get("currentStateSignal"));
        assertFalse(sig.isBlank(), "current-state signal must be non-blank for this message");
        assertTrue(sig.contains("疲惫") || sig.contains("脆弱") || sig.contains("承着"));
    }

    @Test
    @DisplayName("two same-mode users with different portraits now get DIFFERENT prompts (gap closed)")
    void produceReply_sameModeDifferentUsers_differentPrompt() {
        when(safetyService.check(anyString(), anyLong(), anyLong(),
                any(), any(), any())).thenReturn(safe());
        stubCommonDeps();
        when(structuredAiService.call(anyLong(), anyString(), anyString(), any(),
                eq(StructuredAiResults.AuroraResult.class), any(), any())).thenReturn(okResult());

        // USER_A: high intimacy, drive = curiosity.
        UserPortrait aDrive = portrait("INNER_DRIVE", "稳定的好奇心", 0.82, 0.7);
        when(userPortraitService.getAll(USER_A)).thenReturn(List.of(aDrive));
        AgentUserRelationship relA = new AgentUserRelationship();
        relA.relationshipStage = "close_friend";
        relA.intimacyLevel = 8; relA.trustLevel = 7; relA.familiarityLevel = 9; relA.userDisclosureLevel = 6;
        when(relationshipService.getOrInit(USER_A)).thenReturn(relA);

        // USER_B: brand new, value = privacy/caution.
        UserPortrait bVal = portrait("AGENCY_BOUNDARY", "更愿意自己先理清，再分享", 0.7, 0.6);
        when(userPortraitService.getAll(USER_B)).thenReturn(List.of(bVal));
        AgentUserRelationship relB = new AgentUserRelationship();
        relB.relationshipStage = "new_user";
        relB.intimacyLevel = 1; relB.trustLevel = 1; relB.familiarityLevel = 0; relB.userDisclosureLevel = 0;
        when(relationshipService.getOrInit(USER_B)).thenReturn(relB);

        service.replyRich(USER_A, request("今天有点累"));
        service.replyRich(USER_B, request("今天有点累"));

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(structuredAiService, times(2)).call(anyLong(), anyString(), anyString(), cap.capture(),
                eq(StructuredAiResults.AuroraResult.class), any(), any());
        List<Map<String, Object>> contexts = cap.getAllValues();
        String portraitA = String.valueOf(contexts.get(0).get("userPortrait"));
        String portraitB = String.valueOf(contexts.get(1).get("userPortrait"));
        String relationshipA = String.valueOf(contexts.get(0).get("relationship"));
        String relationshipB = String.valueOf(contexts.get(1).get("relationship"));

        assertNotEquals(portraitA, portraitB, "different confirmed portraits must remain distinguishable");
        assertTrue(portraitA.contains("INNER_DRIVE"));
        assertTrue(portraitB.contains("AGENCY_BOUNDARY"));
        assertNotEquals(relationshipA, relationshipB, "different relationships must remain distinguishable");
        assertFalse(contexts.get(0).containsKey("auroraPrompt"));
        assertFalse(contexts.get(1).containsKey("auroraPrompt"));
    }

    @Test
    @DisplayName("model context contains current expression once and keeps one compact personalization copy")
    void produceReply_contextIsBoundedAndNonDuplicated() {
        String current = "CURRENT_EXPRESSION_X9";
        when(safetyService.check(anyString(), anyLong(), anyLong(),
                any(), any(), any())).thenReturn(safe());
        stubCommonDeps();

        AgentContext source = new AgentContext();
        source.recentMessages = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) source.recentMessages.add("user: old-message-" + i);
        source.recentMessages.add("user: " + current);
        source.longTermMemories = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> "memory-" + i + "-" + "x".repeat(400))
                .toList();
        source.timezone = "Asia/Shanghai";
        source.locale = "zh-CN";
        source.localDateTime = "2026-07-27T20:15:00+08:00";
        source.lastInteractionLabel = "距离上次互动 2 小时";
        source.clientTimeHintStatus = "MATCHED";
        source.dailyObservations = List.of("daily-old", "daily-mid", "daily-latest");
        source.weeklyObservations = List.of("weekly-old", "weekly-latest");
        source.threeModelBlock = "PORTRAIT_UNIQUE RELATIONSHIP_UNIQUE CONSTITUTION_UNIQUE";
        source.constitutionBlock = "CONSTITUTION_UNIQUE";
        when(agentContextAssembler.assemble(anyLong(), anyLong(), anyString(), anyBoolean(),
                any(), any(), any(), any(), any())).thenReturn(source);

        UserPortrait portrait = portrait("PORTRAIT_UNIQUE", "confirmed-value", 0.9, 0.8);
        when(userPortraitService.getAll(USER_A)).thenReturn(List.of(portrait));
        AgentUserRelationship relationship = new AgentUserRelationship();
        relationship.relationshipStage = "RELATIONSHIP_UNIQUE";
        relationship.intimacyLevel = 4;
        relationship.trustLevel = 5;
        relationship.familiarityLevel = 6;
        relationship.userDisclosureLevel = 3;
        when(relationshipService.getOrInit(USER_A)).thenReturn(relationship);
        when(structuredAiService.call(anyLong(), anyString(), anyString(), any(),
                eq(StructuredAiResults.AuroraResult.class), any(), any())).thenReturn(okResult());

        service.replyRich(USER_A, request(current));

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(structuredAiService).call(anyLong(), anyString(), anyString(), cap.capture(),
                eq(StructuredAiResults.AuroraResult.class), any(), any());
        Map<String, Object> providerContext = new LinkedHashMap<>(cap.getValue());
        providerContext.remove("auroraSystemPrompt");
        String json = com.innercosmos.util.JsonUtils.toJson(providerContext);

        assertEquals(1, occurrences(json, current), "current user expression must appear exactly once");
        assertEquals(1, occurrences(json, "PORTRAIT_UNIQUE"), "confirmed portrait must have one source");
        assertEquals(1, occurrences(json, "RELATIONSHIP_UNIQUE"), "relationship must have one source");
        assertEquals(1, occurrences(json, "CONSTITUTION_UNIQUE"), "constitution must have one source");
        assertFalse(providerContext.containsKey("auroraPrompt"));
        assertFalse(providerContext.containsKey("recentAuroraMessages"));
        AgentContext compact = (AgentContext) providerContext.get("unifiedAgentContext");
        assertTrue(compact.recentMessages.isEmpty(), "session history must not be duplicated inside AgentContext");
        assertEquals(10, ((List<?>) providerContext.get("conversationHistory")).size());
        assertEquals(4, compact.longTermMemories.size());
        assertEquals("Asia/Shanghai", compact.timezone);
        assertEquals("zh-CN", compact.locale);
        assertEquals("2026-07-27T20:15:00+08:00", compact.localDateTime);
        assertEquals("距离上次互动 2 小时", compact.lastInteractionLabel);
        assertEquals("MATCHED", compact.clientTimeHintStatus);
        assertEquals(List.of("daily-old", "daily-mid", "daily-latest"), compact.dailyObservations);
        assertEquals(List.of("weekly-old", "weekly-latest"), compact.weeklyObservations);
        assertEquals("PORTRAIT_UNIQUE RELATIONSHIP_UNIQUE CONSTITUTION_UNIQUE", compact.threeModelBlock);
        assertTrue(json.length() < 10_000, "bounded model context unexpectedly large: " + json.length());
    }
    @Test
    @DisplayName("mock fallback stays coherent with the state signal when the LLM path fails")
    void fallback_reflectsStateSignal() {
        when(safetyService.check(anyString(), anyLong(), anyLong(),
                any(), any(), any())).thenReturn(safe());
        stubCommonDeps();
        when(userPortraitService.getAll(anyLong())).thenReturn(List.of());
        when(relationshipService.getOrInit(anyLong())).thenReturn(new AgentUserRelationship());
        // Force the LLM path to throw so the differentiatedFallback runs.
        when(structuredAiService.call(anyLong(), anyString(), anyString(), any(),
                eq(StructuredAiResults.AuroraResult.class), any(), any()))
                .thenThrow(new RuntimeException("network error"));

        AuroraReplyVO vo = service.replyRich(USER_A, request("今天好累，撑不住了"));
        assertNotNull(vo);
        assertFalse(vo.messages.isEmpty());
        // The fallback message for a 疲惫/承压 state should gently reflect it (coherent, not a stock string).
        String msg = vo.messages.get(0);
        assertTrue(msg.contains("稳稳接住") || msg.contains("这一刻"),
                "fallback should gently reflect the fragile/tired state signal; got: " + msg);
        assertNotNull(vo.riskFlags);
        assertTrue(vo.riskFlags.contains("EMERGENCY_FALLBACK"));
    }

    private int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) count++;
        return count;
    }
    private UserPortrait portrait(String dim, String valueJson, double confidence, double score) {
        UserPortrait p = new UserPortrait();
        p.dim = dim;
        p.valueJson = valueJson;
        p.confidence = confidence;
        p.score = score;
        return p;
    }
}
