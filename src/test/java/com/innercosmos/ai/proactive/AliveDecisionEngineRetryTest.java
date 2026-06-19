package com.innercosmos.ai.proactive;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.entity.ProactiveEventLog;
import com.innercosmos.mapper.PrivateTimerMapper;
import com.innercosmos.mapper.ProactiveEventLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IC-CORE-001: AliveDecisionEngine.tick() must retry once on LLM failure
 * and must always include the exception object in log.warn calls.
 */
@ExtendWith(MockitoExtension.class)
class AliveDecisionEngineRetryTest {

    @Mock
    private LlmClient llm;

    @Mock
    private QuietWindowResolver quietResolver;

    @Mock
    private PrivateTimerMapper timerMapper;

    @Mock
    private ProactiveEventLogMapper eventLogMapper;

    @Mock
    private ProactiveDeliveryChannel deliveryChannel;

    @Mock
    private UserPortraitService portraitService;

    @Mock
    private AgentUserRelationshipService relationshipService;

    @InjectMocks
    private AliveDecisionEngine engine;

    private static final Long USER_ID = 99L;

    @BeforeEach
    void setUp() {
        // Allow tick to proceed past quiet window check
        QuietWindowResolver.Reason notQuiet = new QuietWindowResolver.Reason(false, null);
        when(quietResolver.canPushNow(eq(USER_ID), any(ZonedDateTime.class))).thenReturn(notQuiet);

        // Allow hard cap check (return 0 pushes in the last hour)
        when(eventLogMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(eventLogMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        // Portrait/relationship stubs
        when(portraitService.getAll(USER_ID)).thenReturn(null);
        when(relationshipService.getOrInit(USER_ID)).thenReturn(null);
    }

    @Test
    void retrySucceeds_llmCalledTwice() {
        // First call throws, second call (retry) succeeds with valid JSON decision
        String validJson = "{\"decide\":\"wait\",\"wait_minutes\":30,\"content_for_user\":\"\",\"reason\":\"test\"}";
        when(llm.chat(any(LlmRequest.class)))
            .thenThrow(new RuntimeException("LLM timeout"))
            .thenReturn(validJson);

        assertDoesNotThrow(() -> engine.tick(USER_ID),
            "tick() must not throw even when first LLM call fails");

        verify(llm, atLeast(2)).chat(any(LlmRequest.class));
    }

    @Test
    void bothCallsFail_noExceptionEscapes() {
        // Both calls throw — engine must swallow the error gracefully
        when(llm.chat(any(LlmRequest.class)))
            .thenThrow(new RuntimeException("LLM timeout"))
            .thenThrow(new RuntimeException("LLM timeout again"));

        assertDoesNotThrow(() -> engine.tick(USER_ID),
            "tick() must not throw even when all LLM retry attempts fail");
    }
}
