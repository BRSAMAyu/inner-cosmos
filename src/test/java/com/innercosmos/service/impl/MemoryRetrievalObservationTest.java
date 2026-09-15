package com.innercosmos.service.impl;

import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryEmbeddingIndexService;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CP-40 §2-19: the retrieval chokepoint must emit one {@code inner.cosmos.memory.retrieval}
 * observation (→ span with the OTel bridge) per retrieval with the bounded attributes
 * {@code task} / {@code top_k} / {@code hits} / {@code outcome} — and never the user id or
 * the raw query text, because retrieval output is P1 memory content on its way into a
 * provider prompt (repo AI tracing discipline, see AiTurnObservation).
 *
 * <p>Verification boundary (honest constraint): assertions capture the observation through
 * micrometer's in-memory {@link TestObservationRegistry}; that proves name/attributes/outcome
 * at the Micrometer layer that micrometer-tracing-bridge-otel converts to OTel spans. It does
 * NOT prove export to a real OTLP collector (deployment-side, opt-in via
 * OTLP_TRACING_ENDPOINT/OTLP_TRACING_ENABLED).
 */
class MemoryRetrievalObservationTest {

    @Test
    void hitPathEmitsRetrievalSpanWithTaskTopKAndHitCount() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                card(1L, "《驱魔人》的结尾", "梅林神父再次面对恶魔与信仰")));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of());

        TestObservationRegistry registry = TestObservationRegistry.create();
        MemoryRetrievalServiceImpl service = wired(mapper, embeddings, registry);

        var result = service.retrieve(7L, new MemoryRetrievalQuery(
                "继续说《驱魔人》里的梅林神父", "AURORA_CONVERSATION", List.of(), 8, 800, false));

        assertThat(result.evidence()).hasSize(1);
        assertThat(registry)
                .hasObservationWithNameEqualTo(MemoryRetrievalServiceImpl.RETRIEVAL_OBSERVATION)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("task", "AURORA_CONVERSATION")
                .hasLowCardinalityKeyValue("top_k", "8")
                .hasLowCardinalityKeyValue("hits", "1")
                .hasLowCardinalityKeyValue("outcome", "OK");
    }

    @Test
    void noHitPathHonestlyReportsZeroHits() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        when(mapper.selectList(any())).thenReturn(List.of(card(1L, "任意近期记忆", "不应被空查询召回")));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of());

        TestObservationRegistry registry = TestObservationRegistry.create();
        MemoryRetrievalServiceImpl service = wired(mapper, embeddings, registry);

        // CP-22 relevance-gate semantics: a blank query retrieves nothing — the span must say 0.
        var result = service.retrieve(7L, new MemoryRetrievalQuery(
                " ", "AURORA_CONVERSATION", List.of(), 6, 800, false));

        assertThat(result.evidence()).isEmpty();
        assertThat(registry)
                .hasObservationWithNameEqualTo(MemoryRetrievalServiceImpl.RETRIEVAL_OBSERVATION)
                .that()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("top_k", "6")
                .hasLowCardinalityKeyValue("hits", "0")
                .hasLowCardinalityKeyValue("outcome", "OK");
    }

    @Test
    void mapperFailureEmitsFailedOutcomeWithBoundedErrorTypeAndRethrows() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        when(mapper.selectList(any())).thenThrow(new IllegalStateException("db down"));

        TestObservationRegistry registry = TestObservationRegistry.create();
        MemoryRetrievalServiceImpl service = wired(mapper, embeddings, registry);

        assertThatThrownBy(() -> service.retrieve(7L, new MemoryRetrievalQuery(
                "任意查询", "AURORA_CONVERSATION", List.of(), 8, 800, false)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry)
                .hasObservationWithNameEqualTo(MemoryRetrievalServiceImpl.RETRIEVAL_OBSERVATION)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("outcome", "FAILED")
                // bounded class token, never the exception message
                .hasLowCardinalityKeyValue("error.type", "IllegalStateException");
    }

    @Test
    void spanNeverCarriesUserIdOrQueryText() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                card(1L, "《驱魔人》的结尾", "梅林神父再次面对恶魔与信仰")));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of());

        TestObservationRegistry registry = TestObservationRegistry.create();
        MemoryRetrievalServiceImpl service = wired(mapper, embeddings, registry);

        // The marker travels ONLY in the query text; if any span attribute value contains it,
        // P0 user wording leaked into tracing.
        String marker = "隐私标记不落span7f3a";
        service.retrieve(7L, new MemoryRetrievalQuery(
                "继续说《驱魔人》里的梅林神父" + marker, "AURORA_CONVERSATION",
                List.of(), 8, 800, false));

        assertThat(registry)
                .hasObservationWithNameEqualTo(MemoryRetrievalServiceImpl.RETRIEVAL_OBSERVATION)
                .that()
                .hasBeenStopped()
                .doesNotHaveLowCardinalityKeyValueWithKey("userId")
                .doesNotHaveLowCardinalityKeyValueWithKey("user_id")
                .doesNotHaveLowCardinalityKeyValueWithKey("query")
                .doesNotHaveLowCardinalityKeyValueWithKey("query_text");

        assertThat(registry).hasHandledContextsThatSatisfy(contexts ->
                contexts.forEach(context -> context.getAllKeyValues().forEach(keyValue -> {
                    assertTrue(!keyValue.getValue().contains(marker),
                            "span attribute " + keyValue.getKey() + " leaked query content: " + keyValue.getValue());
                })));
    }

    private static MemoryRetrievalServiceImpl wired(MemoryCardMapper mapper,
                                                    MemoryEmbeddingIndexService embeddings,
                                                    TestObservationRegistry registry) {
        MemoryRetrievalServiceImpl service = new MemoryRetrievalServiceImpl(mapper, embeddings);
        ReflectionTestUtils.setField(service, "observationRegistry", registry);
        return service;
    }

    private static MemoryCard card(long id, String title, String summary) {
        MemoryCard card = new MemoryCard();
        card.id = id;
        card.userId = 7L;
        card.title = title;
        card.summary = summary;
        card.memoryType = "EVENT";
        card.memoryLayer = "EPISODIC";
        card.status = "ACTIVE";
        card.versionNo = 1;
        card.confidence = .9;
        card.emotionalGravity = 1.0;
        card.consentScope = "AURORA_PRIVATE";
        card.createdAt = LocalDateTime.now();
        return card;
    }
}
