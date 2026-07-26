package com.innercosmos.ai.agent;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.router.ResolvedModel;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapsuleAgentPersonaSynthesisTest {
    @Mock StructuredAiService structuredAiService;
    @Mock SessionModelRouter modelRouter;
    @Mock LlmClient provider;
    @Mock LlmConfig llmConfig;

    @Test
    void usesTheUsersResolvedRealProviderAndFollowsEvidenceLanguage() {
        when(modelRouter.resolve(42L, null)).thenReturn(new ResolvedModel("DEEPSEEK", "deepseek-chat", provider));
        when(provider.chat(any())).thenReturn("Speak with quiet precision and never invent a memory.");
        CapsuleAgent agent = new CapsuleAgent(structuredAiService, modelRouter, llmConfig);

        String persona = agent.generateUserPersona(42L,
                List.of("I care about building things carefully, but I tense up before presenting them."),
                "After the rain", "A part of me that stays honest under pressure.");

        assertEquals("Speak with quiet precision and never invent a memory.", persona);
        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(provider).chat(request.capture());
        assertEquals("DEEPSEEK", request.getValue().preferredProvider);
        assertTrue(request.getValue().prompt.contains("dominant language"));
        assertTrue(request.getValue().prompt.contains("I care about building things carefully"));
    }

    @Test
    void failsClosedWhenOnlyMockIsResolvedInProductionEvenWithNoMemory() {
        when(modelRouter.resolve(42L, null)).thenReturn(new ResolvedModel("MOCK", "mock-inner-cosmos", provider));
        when(llmConfig.isProdMode()).thenReturn(true);
        CapsuleAgent agent = new CapsuleAgent(structuredAiService, modelRouter, llmConfig);

        BusinessException error = assertThrows(BusinessException.class,
                () -> agent.generateUserPersona(42L, List.of(), "New facet", "Still getting to know me."));

        assertEquals("AI_PROVIDER_ERROR", error.code);
    }

    @Test
    void permitsTheExplicitlyLabelledOfflineProviderOnlyOutsideProduction() {
        when(modelRouter.resolve(42L, null)).thenReturn(new ResolvedModel("MOCK", "mock-inner-cosmos", provider));
        when(provider.chat(any())).thenReturn("OFFLINE_TEST_PROVIDER_OUTPUT");
        CapsuleAgent agent = new CapsuleAgent(structuredAiService, modelRouter, llmConfig);

        assertEquals("OFFLINE_TEST_PROVIDER_OUTPUT",
                agent.generateUserPersona(42L, List.of("test evidence"), "Test facet", "Test only"));
    }

    @Test
    void neverTurnsProviderFailureIntoAPublishableTemplate() {
        when(modelRouter.resolve(42L, null)).thenReturn(new ResolvedModel("GLM", "glm-5", provider));
        when(provider.chat(any())).thenThrow(new IllegalStateException("429"));
        CapsuleAgent agent = new CapsuleAgent(structuredAiService, modelRouter, llmConfig);

        BusinessException error = assertThrows(BusinessException.class,
                () -> agent.generateUserPersona(42L, List.of("我重视坦诚。"), "雨后", "不急着美化自己。"));

        assertEquals("AI_PROVIDER_ERROR", error.code);
        assertTrue(error.getMessage().contains("未创建模板替身"));
    }
}
