package com.innercosmos.service.impl;

import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.mapper.AiInteractionLogMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiLogServiceImplMetricsTest {
    @Test
    void recordsPrivacySafeProviderLatencyAndExplicitEstimatedTokens() {
        AiInteractionLogMapper mapper = mock(AiInteractionLogMapper.class);
        when(mapper.insert(any(AiInteractionLog.class))).thenReturn(1);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiLogServiceImpl service = new AiLogServiceImpl(mapper, registry);

        service.recordDetailed(42L, "MEMORY_EXTRACT", "DeepSeek", "model",
                "12345678", "1234", null, null, true, false, null, 1250);

        assertEquals(1.0, registry.get("inner.cosmos.ai.provider.calls")
                .tag("provider", "deepseek").tag("outcome", "success").counter().count());
        assertEquals(4.0, registry.get("inner.cosmos.ai.tokens.estimated")
                .tag("direction", "input").counter().count());
        assertEquals(2.0, registry.get("inner.cosmos.ai.tokens.estimated")
                .tag("direction", "output").counter().count());
        assertEquals(1, registry.get("inner.cosmos.ai.provider.latency").timer().count());
    }
}
