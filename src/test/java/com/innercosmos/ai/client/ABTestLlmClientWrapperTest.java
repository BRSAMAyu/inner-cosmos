package com.innercosmos.ai.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** M-006: PII (phone/email) must be masked before the prompt is egressed to a provider. */
class ABTestLlmClientWrapperTest {

    @Test
    @DisplayName("M-006: chat() masks phone/email in prompt + recentMessages before delegating")
    void chat_masksPiiBeforeDelegation() {
        LlmClient delegate = mock(LlmClient.class);
        when(delegate.chat(any())).thenReturn("ok");

        ABTestLlmClientWrapper wrapper = new ABTestLlmClientWrapper(delegate, null, Runnable::run);

        LlmRequest request = new LlmRequest(1L, "TEST",
                "我今天有点累，手机13812345678，邮箱 a@b.com");
        request.recentMessages = new ArrayList<>(List.of("回我电话 13900001111"));

        wrapper.chat(request);

        verify(delegate).chat(request);
        assertFalse(request.prompt.contains("13812345678"), "phone must be masked in prompt");
        assertTrue(request.prompt.contains("[数字已脱敏]"));
        assertFalse(request.prompt.contains("a@b.com"), "email must be masked in prompt");
        assertTrue(request.prompt.contains("[邮箱已脱敏]"));
        assertFalse(request.recentMessages.get(0).contains("13900001111"), "phone masked in recentMessages");
        // Emotional content is preserved.
        assertTrue(request.prompt.contains("我今天有点累"));
    }
}
