package com.innercosmos.service.impl;

import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryEmbeddingIndexService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryRetrievalConsentBoundaryTest {

    @Test
    void evidencePackExcludesNoEgressMemoriesAndCarriesAuthorityMetadata() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryEmbeddingIndexService embeddings = mock(MemoryEmbeddingIndexService.class);
        MemoryCard allowed = card(11L, "AURORA_PRIVATE", "VOICE_TRANSCRIPTION:71", "蓝色折纸船");
        MemoryCard localOnly = card(12L, "LOCAL_ONLY", "HEART_DIARY:72", "蓝色折纸船");
        MemoryCard noExternal = card(13L, "NO_EXTERNAL_PROCESSING", "THOUGHT_SHREDDER:73", "蓝色折纸船");
        MemoryCard simulatorOnly = card(14L, "SIMULATOR_AUTHORIZED", "SIMULATOR:74", "蓝色折纸船");
        when(mapper.selectList(any())).thenReturn(List.of(allowed, localOnly, noExternal, simulatorOnly));
        when(embeddings.similarities(eq(7L), eq("蓝色折纸船"), any())).thenReturn(Map.of(11L, 0.9));

        var service = new MemoryRetrievalServiceImpl(mapper, embeddings);
        var pack = service.retrieve(7L, new MemoryRetrievalQuery(
                "蓝色折纸船", "AURORA_CONVERSATION", List.of(), 8, 800, false));

        assertThat(pack.evidence()).extracting(evidence -> evidence.memoryId())
                .containsExactly(11L);
        assertThat(pack.evidence().getFirst().versionNo()).isEqualTo(1);
        assertThat(pack.evidence().getFirst().consentScope()).isEqualTo("AURORA_PRIVATE");
        assertThat(pack.evidence().getFirst().provenanceRefs()).isEqualTo("VOICE_TRANSCRIPTION:71");
        assertThat(pack.excludedStatuses()).contains(
                "CONSENT_LOCAL_ONLY", "CONSENT_NO_EXTERNAL_PROCESSING", "CONSENT_SIMULATOR_ONLY");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemoryCard>> allowedCandidates = ArgumentCaptor.forClass(List.class);
        verify(embeddings).similarities(eq(7L), eq("蓝色折纸船"), allowedCandidates.capture());
        assertThat(allowedCandidates.getValue()).extracting(card -> card.id).containsExactly(11L);
    }

    private MemoryCard card(Long id, String consentScope, String provenance, String phrase) {
        MemoryCard card = new MemoryCard();
        card.id = id;
        card.userId = 7L;
        card.title = "日记线索";
        card.summary = phrase;
        card.status = "ACTIVE";
        card.memoryLayer = "EPISODIC";
        card.versionNo = 1;
        card.consentScope = consentScope;
        card.provenanceRefs = provenance;
        card.confidence = 0.9;
        card.emotionalGravity = 1.0;
        card.lastTouchedAt = LocalDateTime.now();
        return card;
    }
}
