package com.innercosmos.ai.goodbye;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoodbyeOrchestratorOwnershipTest {

    @Mock FarewellTemplates templates;
    @Mock GoodbyeLineGenerator lineGen;
    @Mock SessionCloser closer;
    @Mock GoodbyeSessionAccess sessionAccess;
    @Mock GoodbyeTriggerDetector goodbyeTriggerDetector;
    @InjectMocks GoodbyeOrchestrator orchestrator;

    @BeforeEach
    void defaults() {
        lenient().when(goodbyeTriggerDetector.getLastStrength()).thenReturn("LANGUAGE_HIGH");
    }

    @Test
    void ownerRunsLlmAndAsyncPipelineAfterClaim() {
        when(sessionAccess.claim(7L, 42L, "BUTTON"))
                .thenReturn(GoodbyeSessionAccess.ClaimResult.CLAIMED);
        when(lineGen.generate(7L, 42L, "BUTTON"))
                .thenReturn(CompletableFuture.completedFuture("goodbye"));

        GoodbyeResult result = orchestrator.start(7L, 42L, "BUTTON");

        assertThat(result.success).isTrue();
        verify(closer).runAfterGoodbye(7L, 42L, "LANGUAGE_HIGH");
    }

    @Test
    void nonOwnerMissingAndAdminAreRejectedBeforeEverySideEffect() {
        BusinessException opaque = new BusinessException(ErrorCode.NOT_FOUND, "对话会话不存在或不可访问");
        when(sessionAccess.claim(anyLong(), eq(42L), eq("BUTTON"))).thenThrow(opaque);

        assertRejected(() -> orchestrator.start(7L, 42L, "BUTTON"));
        assertRejected(() -> orchestrator.start(1L, 42L, "BUTTON"));

        verifyNoInteractions(lineGen, closer, templates);
        verify(goodbyeTriggerDetector, never()).getLastStrength();
    }

    @Test
    void duplicateCallDoesNotRepeatLlmOrAsyncWork() {
        when(sessionAccess.claim(7L, 42L, "BUTTON"))
                .thenReturn(GoodbyeSessionAccess.ClaimResult.ALREADY_STARTED);
        when(templates.forTrigger("BUTTON")).thenReturn("already handled");

        GoodbyeResult result = orchestrator.start(7L, 42L, "BUTTON");

        assertThat(result.success).isTrue();
        verifyNoInteractions(lineGen, closer);
    }

    private void assertRejected(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }
}
