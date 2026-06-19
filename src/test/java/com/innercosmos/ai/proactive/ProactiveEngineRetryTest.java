package com.innercosmos.ai.proactive;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IC-CORE-001: ProactiveEngine.generateContent() must retry once on LLM failure
 * and fall back to the default string if both attempts fail.
 */
@ExtendWith(MockitoExtension.class)
class ProactiveEngineRetryTest {

    @Mock
    private LlmClient llm;

    @InjectMocks
    private ProactiveEngine engine;

    // Helper: invoke the private generateContent(Long, String) via reflection
    private String invokeGenerateContent(Long userId, String triggerType) throws Exception {
        Method m = ProactiveEngine.class.getDeclaredMethod("generateContent", Long.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(engine, userId, triggerType);
    }

    @Test
    void retrySucceeds_returnsRetryResult() throws Exception {
        // First call throws, second call (retry) succeeds
        when(llm.chat(any(LlmRequest.class)))
            .thenThrow(new RuntimeException("LLM timeout"))
            .thenReturn("retry-success-content");

        String result = invokeGenerateContent(1L, "scheduled");

        assertEquals("retry-success-content", result,
            "Should return the retry result when first call fails and retry succeeds");
        verify(llm, times(2)).chat(any(LlmRequest.class));
    }

    @Test
    void bothCallsFail_returnsFallback() throws Exception {
        // Both calls throw
        when(llm.chat(any(LlmRequest.class)))
            .thenThrow(new RuntimeException("LLM timeout"))
            .thenThrow(new RuntimeException("LLM timeout again"));

        String result = assertDoesNotThrow(() -> invokeGenerateContent(1L, "scheduled"),
            "Should not throw when both LLM calls fail");

        assertEquals("你好，今天过得怎么样？", result,
            "Should return the fallback string when both attempts fail");
        verify(llm, times(2)).chat(any(LlmRequest.class));
    }
}
