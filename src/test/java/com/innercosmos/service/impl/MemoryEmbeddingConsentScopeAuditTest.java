package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AUDIT PROBE: does SIMULATOR_AUTHORIZED memory content ever reach the third-party embedding
 * provider? MemoryRetrievalServiceImpl.PROVIDER_FORBIDDEN_CONSENT and
 * CapsuleServiceImpl.consentScopedSemanticQueryText both treat SIMULATOR_AUTHORIZED as
 * provider-forbidden; this asserts the memory indexing path agrees.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryEmbeddingConsentScopeAuditTest {

    @Mock private MemoryEmbeddingClient client;
    @Mock private MemoryEmbeddingMapper mapper;
    @Mock private MemoryCardMapper memoryMapper;
    @Mock private JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MemoryCard simulatorOnlyCard() {
        MemoryCard c = new MemoryCard();
        c.id = 1L;
        c.userId = 10L;
        c.title = "只授权给模拟器的私密记忆";
        c.summary = "这段内容用户只同意用于模拟器测试";
        c.status = "ACTIVE";
        c.consentScope = "SIMULATOR_AUTHORIZED";
        c.versionNo = 1;
        return c;
    }

    @Test
    @DisplayName("rebuildMissing must NOT send SIMULATOR_AUTHORIZED memory text to the embedding provider")
    void rebuildMissing_neverEmbedsSimulatorAuthorizedMemory() {
        when(client.available()).thenReturn(true);
        when(client.modelName()).thenReturn("text-embedding-v4");
        when(client.modelVersion()).thenReturn("2026-01");
        when(client.embed(anyString())).thenReturn(new float[1536]);
        when(mapper.selectMissingMemoryIds(anyString(), anyString(), anyInt())).thenReturn(List.of(1L));
        when(memoryMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(simulatorOnlyCard()));
        when(mapper.selectCount(any())).thenReturn(0L);

        new MemoryEmbeddingIndexServiceImpl(client, mapper, memoryMapper, jdbc, objectMapper)
                .rebuildMissing(10);

        verify(client, never()).embed(anyString());
    }

    @Test
    @DisplayName("similarities() must NOT embed a query on behalf of a SIMULATOR_AUTHORIZED-only candidate set")
    void similarities_neverCallsProviderForSimulatorOnlyCandidates() {
        when(client.available()).thenReturn(true);
        when(client.embed(anyString())).thenReturn(new float[1536]);

        new MemoryEmbeddingIndexServiceImpl(client, mapper, memoryMapper, jdbc, objectMapper)
                .similarities(10L, "蓝色折纸船", List.of(simulatorOnlyCard()));

        verify(client, never()).embed(anyString());
    }
}
