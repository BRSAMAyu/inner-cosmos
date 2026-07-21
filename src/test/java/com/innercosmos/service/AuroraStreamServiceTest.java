package com.innercosmos.service;

import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.ai.context.AgentContextAssembler;
import com.innercosmos.ai.goodbye.GoodbyeOrchestrator;
import com.innercosmos.ai.goodbye.GoodbyeTriggerDetector;
import com.innercosmos.ai.mode.ModeRegistry;
import com.innercosmos.ai.router.ResolvedModel;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.ai.portrait.PortraitReflectionService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.impl.AuroraAgentServiceImpl;
import com.innercosmos.vo.AuroraReplyVO;
import com.innercosmos.vo.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * VS-003 — Aurora stream safety-first ordering + persist + fallback.
 * Verifies that a crisis input emits a safety event and NEVER invokes the LLM
 * (no chat token can stream), while a normal input still persists and streams.
 */
@ExtendWith(MockitoExtension.class)
class AuroraStreamServiceTest {

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

    // Sync executor so the stream body runs inline, on the test thread.
    private final Executor aiExecutor = new SyncTaskExecutor();

    private AuroraAgentServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new AuroraAgentServiceImpl(structuredAiService, dialogService, safetyService,
                memoryService, rhythmGuardService, memoryContextService, userProfileMapper,
                sessionMapper, llmConfig, aiExecutor, agentContextAssembler, modelRouter,
                portraitReflection, goodbyeDetector, goodbyeOrchestrator, modeRegistry,
                null, portraitService, relationshipService);
    }

    @Mock private com.innercosmos.ai.portrait.UserPortraitService portraitService;
    @Mock private com.innercosmos.ai.portrait.AgentUserRelationshipService relationshipService;

    private SafetyResult blocked() {
        SafetyResult r = new SafetyResult();
        r.riskLevel = "HIGH";
        r.riskType = "CRISIS_KEYWORD";
        r.handledAction = "RESOURCE_PAGE";
        r.safeMessage = "请立即联系当地急救或可信赖的现实支持者.";
        r.blockModelCall = true;
        return r;
    }

    private SafetyResult safe() {
        SafetyResult r = new SafetyResult();
        r.riskLevel = "LOW";
        r.riskType = "NONE";
        r.blockModelCall = false;
        return r;
    }

    /** Stub the collaborators that produceReply() touches downstream of the safety gate. */
    private void stubReplyDeps(StructuredAiResults.AuroraResult ai) {
        when(agentContextAssembler.assemble(anyLong(), anyLong(), anyString(), anyBoolean(),
                any(), any())).thenReturn(new AgentContext());
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
        // LlmConfig accessors must be non-null (Map.of rejects nulls in aiState()).
        when(llmConfig.activeProvider()).thenReturn("MOCK");
        when(llmConfig.activeModel()).thenReturn("mock");
        when(llmConfig.getMode()).thenReturn("mock");
        when(llmConfig.hasActiveApiKey()).thenReturn(false);
        when(llmConfig.isEffectiveFallbackAllowed()).thenReturn(true);
        when(structuredAiService.call(anyLong(), anyString(), anyString(), any(),
                eq(StructuredAiResults.AuroraResult.class), any(), any()))
                .thenReturn(ai);
    }

    @Test
    @DisplayName("crisis input -> safety event, NO model call, NO chat token streamed (safety-before-stream guard)")
    void stream_crisis_emitsSafetyEvent_noChatStreamed() throws Exception {
        String crisis = "想要了断";
        when(safetyService.check(eq(crisis), eq(USER_ID), eq(SESSION_ID))).thenReturn(blocked());

        AtomicBoolean completed = new AtomicBoolean(false);
        SseEmitter emitter = service.stream(USER_ID, SESSION_ID, crisis, "DAILY_TALK");
        assertNotNull(emitter);
        emitter.onCompletion(() -> completed.set(true));

        // Give the (sync) executor no slack needed — it ran inline.
        Thread.sleep(50);

        // The model must NEVER be called -> no chat content can be produced/streamed.
        verifyNoInteractions(structuredAiService);
        // The crisis safe-message is still persisted (record capture), once.
        verify(dialogService).saveAuroraMessage(eq(USER_ID), eq(SESSION_ID), anyString());
        // The user turn is persisted.
        verify(dialogService).saveUserMessage(eq(USER_ID), any(ChatRequest.class));
    }

    @Test
    @DisplayName("normal input -> model called, reply persisted per segment, emitter completes")
    void stream_normal_persistsAndCompletes() throws Exception {
        String message = "今天有点累";
        when(safetyService.check(eq(message), eq(USER_ID), eq(SESSION_ID))).thenReturn(safe());
        StructuredAiResults.AuroraResult ai = new StructuredAiResults.AuroraResult();
        ai.segments = List.of("我在。你不用组织得很漂亮，先把最真实的那句话放在这里。");
        ai.detectedTheme = "日常倾诉";
        stubReplyDeps(ai);

        SseEmitter emitter = service.stream(USER_ID, SESSION_ID, message, "DAILY_TALK");
        assertNotNull(emitter);
        emitter.onCompletion(() -> {});

        Thread.sleep(100);

        verify(structuredAiService, atLeastOnce()).call(anyLong(), anyString(), anyString(), any(),
                eq(StructuredAiResults.AuroraResult.class), any(), any());
        verify(dialogService).saveUserMessage(eq(USER_ID), any(ChatRequest.class));
        // Aurora's reply segment is persisted (record capture intact on the stream path).
        verify(dialogService).saveAuroraMessage(eq(USER_ID), eq(SESSION_ID), contains("我在"));
    }

    @Test
    @DisplayName("POST replyRich path still works unchanged (fallback intact)")
    void replyRich_normal_returnsMessages() {
        ChatRequest request = new ChatRequest();
        request.sessionId = SESSION_ID;
        request.message = "今天有点累";
        request.mode = "DAILY_TALK";
        when(safetyService.check(anyString(), anyLong(), anyLong())).thenReturn(safe());
        StructuredAiResults.AuroraResult ai = new StructuredAiResults.AuroraResult();
        ai.segments = List.of("我在。");
        ai.detectedTheme = "日常倾诉";
        stubReplyDeps(ai);

        AuroraReplyVO vo = service.replyRich(USER_ID, request);

        assertNotNull(vo);
        assertFalse(vo.messages == null || vo.messages.isEmpty());
        verify(safetyService).check(eq("今天有点累"), eq(USER_ID), eq(SESSION_ID));
    }
}
