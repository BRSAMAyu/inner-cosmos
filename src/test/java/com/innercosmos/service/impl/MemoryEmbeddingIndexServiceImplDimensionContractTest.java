package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.EmbeddingDimensionMismatchException;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini audit 2.6 (CONFIRMED/P1): both the Java write path and the PostgreSQL
 * {@code tb_memory_embedding.embedding_vector} column (V10) hardcode a fixed 1536-dimension
 * embedding. Before this fix, {@code MemoryEmbeddingIndexServiceImpl} silently zero-padded (if
 * shorter) or truncated (if longer) any embedding vector to force-fit that fixed width --
 * {@code memory.embedding.dimensions} is operator-configurable in [8, 1536]
 * (see MemoryEmbeddingConfig), so a real, reachable operator misconfiguration (or a future model
 * swap to a smaller/larger dimension) would silently corrupt every stored/queried vector instead
 * of failing fast.
 *
 * This test proves the PostgreSQL write/query path now REJECTS a dimension mismatch instead of
 * silently force-fitting it. (The H2/local JSON path is dimension-agnostic and untouched --
 * MemoryEmbeddingCandidateIntegrationTest already exercises it with an 8-dimension fake client
 * against H2, and must keep passing unmodified.)
 */
@ExtendWith(MockitoExtension.class)
class MemoryEmbeddingIndexServiceImplDimensionContractTest {

    @Mock private MemoryEmbeddingClient client;
    @Mock private MemoryEmbeddingMapper mapper;
    @Mock private MemoryCardMapper memoryMapper;
    @Mock private JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MemoryEmbeddingIndexServiceImpl service() {
        return new MemoryEmbeddingIndexServiceImpl(client, mapper, memoryMapper, jdbc, objectMapper);
    }

    /** Makes isPostgres() (a jdbc.execute(ConnectionCallback) probe) report "yes, PostgreSQL". */
    @SuppressWarnings("unchecked")
    private void simulatePostgres() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<Boolean> callback = invocation.getArgument(0);
            Connection connection = mock(Connection.class);
            DatabaseMetaData metaData = mock(DatabaseMetaData.class);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
            return callback.doInConnection(connection);
        });
    }

    private MemoryCard card(long id, long userId, int versionNo) {
        MemoryCard c = new MemoryCard();
        c.id = id;
        c.userId = userId;
        c.title = "t";
        c.summary = "s";
        c.status = "ACTIVE";
        c.consentScope = "AURORA_PRIVATE";
        c.versionNo = versionNo;
        return c;
    }

    @Test
    @DisplayName("2.6: indexing a card whose embedding vector does NOT match the fixed vector(1536) column dimension fails fast, never force-fit-written")
    void indexIfMissing_dimensionMismatch_failsFastWithoutWriting() {
        simulatePostgres();
        when(client.available()).thenReturn(true);
        when(client.modelName()).thenReturn("swapped-model");
        when(client.modelVersion()).thenReturn("v2");
        // A real, reachable misconfiguration: memory.embedding.dimensions=768 is within
        // MemoryEmbeddingConfig's allowed [8,1536] range, but the PostgreSQL column is a fixed
        // vector(1536) -- 768 != 1536 must be rejected, not zero-padded to fit.
        float[] mismatched = new float[768];
        when(client.embed(anyString())).thenReturn(mismatched);
        when(mapper.selectMissingMemoryIds(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(1L));
        when(memoryMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(card(1L, 10L, 1)));
        when(mapper.selectCount(any())).thenReturn(0L);

        var result = service().rebuildMissing(10);

        assertEquals(0, result.indexed(), "a dimension-mismatched vector must NOT count as indexed");
        assertEquals(1, result.failed(), "must be counted as a failure, not silently accepted");
        // Never write a force-fit/corrupted vector into the fixed-width column.
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(mapper, never()).insert(any(MemoryEmbedding.class));
    }

    @Test
    @DisplayName("2.6: a query-time embedding whose dimension mismatches the stored column also fails fast rather than silently comparing padded vectors")
    void similarities_dimensionMismatch_failsFastAndFallsBackGracefully() {
        simulatePostgres();
        when(client.available()).thenReturn(true);
        when(client.embed(anyString())).thenReturn(new float[768]);
        MemoryCard eligible = card(2L, 20L, 1);
        eligible.consentScope = "AURORA_PRIVATE";

        var scores = service().similarities(20L, "some query", List.of(eligible));

        // similarities() treats any embedding-path failure as "no provider candidates this turn"
        // -- caller falls back to the deterministic lexical path, never crashes the request and
        // never returns scores computed from a corrupted comparison.
        assertEquals(java.util.Map.of(), scores);
        verify(jdbc, never()).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    @DisplayName("2.6: a correctly-sized (1536-dim) vector still indexes and writes normally -- the guard only rejects a real mismatch")
    void indexIfMissing_matchingDimension_stillWritesNormally() {
        simulatePostgres();
        when(client.available()).thenReturn(true);
        when(client.modelName()).thenReturn("text-embedding-3-small");
        when(client.modelVersion()).thenReturn("2026-01");
        when(client.embed(anyString())).thenReturn(new float[1536]);
        when(mapper.selectMissingMemoryIds(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(3L));
        when(memoryMapper.selectBatchIds(List.of(3L))).thenReturn(List.of(card(3L, 30L, 1)));
        when(mapper.selectCount(any())).thenReturn(0L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var result = service().rebuildMissing(10);

        assertEquals(1, result.indexed());
        assertEquals(0, result.failed());
        verify(mapper).insert(any(MemoryEmbedding.class));
        verify(jdbc).update(anyString(), any(Object[].class));
    }
}
