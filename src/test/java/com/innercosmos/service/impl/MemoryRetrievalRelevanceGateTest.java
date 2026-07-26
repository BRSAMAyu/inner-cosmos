package com.innercosmos.service.impl;

import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryEmbeddingIndexService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryRetrievalRelevanceGateTest {

    @Test
    void unrelatedHighFreshnessAndTaskFitCannotCrossAdmissionGate() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        MemoryCard spiritedAway = card(1L, "《千与千寻》的成长隐喻",
                "千寻在异世界学会勇敢与告别");
        spiritedAway.confidence = 1.0;
        spiritedAway.emotionalGravity = 3.0;
        spiritedAway.lastTouchedAt = LocalDateTime.now();
        when(mapper.selectList(any())).thenReturn(List.of(spiritedAway));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of());

        var service = new MemoryRetrievalServiceImpl(mapper, embeddings);
        var result = service.retrieve(7L, new MemoryRetrievalQuery(
                "《驱魔人》里梅林神父为什么回来", "AURORA_CONVERSATION",
                List.of(), 8, 800, false));

        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void lexicalTopicMatchAdmitsExorcistButRejectsSpiritedAway() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        MemoryCard exorcist = card(1L, "《驱魔人》的结尾",
                "梅林神父再次面对恶魔与信仰");
        MemoryCard spiritedAway = card(2L, "《千与千寻》的成长隐喻",
                "千寻在异世界学会勇敢与告别");
        when(mapper.selectList(any())).thenReturn(List.of(spiritedAway, exorcist));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of());

        var service = new MemoryRetrievalServiceImpl(mapper, embeddings);
        var result = service.retrieve(7L, new MemoryRetrievalQuery(
                "继续说《驱魔人》里的梅林神父", "AURORA_CONVERSATION",
                List.of(), 8, 800, false));

        assertThat(result.evidence()).extracting(evidence -> evidence.memoryId())
                .containsExactly(1L);
    }

    @Test
    void blankQueryReturnsNoMemoriesInsteadOfARecencyRankedSample() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        when(mapper.selectList(any())).thenReturn(List.of(card(1L, "任意近期记忆", "不应被空查询召回")));
        when(embeddings.similarities(eq(7L), any(), any())).thenReturn(Map.of());

        var service = new MemoryRetrievalServiceImpl(mapper, embeddings);
        var result = service.retrieve(7L, new MemoryRetrievalQuery(
                " ", "AURORA_CONVERSATION", List.of(), 8, 800, false));

        assertThat(result.evidence()).isEmpty();
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
