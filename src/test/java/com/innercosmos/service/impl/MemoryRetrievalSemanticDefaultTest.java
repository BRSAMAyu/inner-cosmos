package com.innercosmos.service.impl;

import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryEmbeddingIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Provider embeddings must widen recall in the shipped configuration, not only after an operator
 * installs a fitted calibration. Before this, {@code semantic-calibration.enabled} defaulted to
 * false and {@code calibratedProviderAdmission} returned an empty map in that case, so a deployment
 * could call a real embedding provider, store real 1536-dimension vectors, and have them contribute
 * exactly nothing to the Evidence Pack. These tests pin the out-of-the-box behaviour.
 *
 * <p>Every query below is lexically disjoint from every memory, so admission can only come from the
 * provider vector channel -- {@link MemoryRetrievalRelevanceGateTest} covers the lexical path and
 * the fail-closed-on-model-change contract.
 */
class MemoryRetrievalSemanticDefaultTest {

    @Test
    @DisplayName("a strong provider cosine admits a lexically-disjoint memory with no operator calibration")
    void providerVectorsContributeOutOfTheBox() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                card(1L, "需要先留一点空间", "难受的时候请先陪着我，不要立刻给建议")));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of(1L, 0.82));

        // Two-argument constructor == the default, un-calibrated production wiring.
        var pack = new MemoryRetrievalServiceImpl(mapper, embeddings).retrieve(7L,
                new MemoryRetrievalQuery("我希望你别急着安慰我", "AURORA_CONVERSATION",
                        List.of(), 6, 800, false));

        assertThat(pack.evidence()).extracting(evidence -> evidence.memoryId()).containsExactly(1L);
        assertThat(pack.evidence().getFirst().contributions())
                .anyMatch(reason -> reason.contains("provider"));
    }

    @Test
    @DisplayName("every memory clearing the threshold is admitted, not only the single top-scoring one")
    void allAboveThresholdMemoriesAreAdmitted() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                card(1L, "需要先留一点空间", "难受时请先陪我"),
                card(2L, "被安慰得太快会更闷", "希望对方先听完整件事"),
                card(3L, "给植物换盆", "准备新的土和陶盆")));
        // 0.81 and 0.63 clear the built-in zh threshold; 0.22 does not.
        when(embeddings.similarities(eq(7L), any(), any()))
                .thenReturn(Map.of(1L, 0.81, 2L, 0.63, 3L, 0.22));

        var pack = new MemoryRetrievalServiceImpl(mapper, embeddings).retrieve(7L,
                new MemoryRetrievalQuery("我希望你别急着安慰我", "AURORA_CONVERSATION",
                        List.of(), 6, 800, false));

        assertThat(pack.evidence()).extracting(evidence -> evidence.memoryId())
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("weak cosines still fail closed by default, so the vector channel cannot flood the prompt")
    void weakCosinesRemainRejectedByDefault() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                card(1L, "给植物换盆", "准备新的土和陶盆"),
                card(2L, "周末整理厨房", "清点香料并擦拭架子")));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of(1L, 0.41, 2L, 0.33));

        var pack = new MemoryRetrievalServiceImpl(mapper, embeddings).retrieve(7L,
                new MemoryRetrievalQuery("我希望你别急着安慰我", "AURORA_CONVERSATION",
                        List.of(), 6, 800, false));

        assertThat(pack.evidence()).isEmpty();
    }

    @Test
    @DisplayName("token budget counts a Han character as roughly one token, not a third of one")
    void tokenBudgetIsScriptAware() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        // 60 Han characters of title+summary. The old characters/3 rule scored this at 20 tokens.
        MemoryCard bulky = card(1L, "记忆标题".repeat(5), "这是一段相当长的中文摘要内容".repeat(3));
        when(mapper.selectList(any())).thenReturn(List.of(bulky));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of(1L, 0.9));

        var pack = new MemoryRetrievalServiceImpl(mapper, embeddings).retrieve(7L,
                new MemoryRetrievalQuery("我希望你别急着安慰我", "AURORA_CONVERSATION",
                        List.of(), 6, 800, false));

        int hanCharacters = bulky.title.length() + bulky.summary.length();
        assertThat(pack.estimatedTokens())
                .as("a Chinese Evidence Pack must not under-report its own prompt cost")
                .isGreaterThanOrEqualTo(hanCharacters);
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
        return card;
    }
}
