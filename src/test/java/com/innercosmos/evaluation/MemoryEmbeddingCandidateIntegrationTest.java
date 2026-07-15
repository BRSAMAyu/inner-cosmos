package com.innercosmos.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.innercosmos.service.MemoryRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:embedding-candidate;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
@Import(MemoryEmbeddingCandidateIntegrationTest.FakeEmbeddingConfig.class)
class MemoryEmbeddingCandidateIntegrationTest {
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryEmbeddingMapper embeddingMapper;
    @Autowired MemoryRetrievalService retrieval;

    @Test
    void providerCandidateFindsParaphraseWithoutCrossingOwnerOrStatusGates() {
        long owner = 92001L;
        MemoryCard relevant = card(owner, "提交课程报告", "整理实验结果并交付", "ACTIVE");
        card(owner, "雨天散步", "慢下来观察街道", "ACTIVE");
        card(owner, "旧课程判断", "已经被纠正", "CONTRADICTED");
        MemoryCard localOnly = card(owner, "本地私密课程记录", "不得发送给外部 embedding provider", "ACTIVE");
        localOnly.consentScope = "LOCAL_ONLY"; memoryMapper.updateById(localOnly);
        card(92002L, "提交课程报告", "另一个用户的内容", "ACTIVE");

        var pack = retrieval.retrieve(owner, new MemoryRetrievalQuery(
                "deadline deliverable", "ACTION_SPLIT", null, 3, 120, false));
        assertEquals(relevant.id, pack.evidence().get(0).memoryId());
        assertEquals(2, embeddingMapper.selectCount(new QueryWrapper<MemoryEmbedding>().eq("user_id", owner)));
        assertEquals("provider-contract-model", embeddingMapper.selectList(
                new QueryWrapper<MemoryEmbedding>().eq("user_id", owner)).get(0).modelName);
    }

    private MemoryCard card(long userId, String title, String summary, String status) {
        MemoryCard card = new MemoryCard(); card.userId = userId; card.title = title; card.summary = summary;
        card.memoryType = "TODO"; card.memoryLayer = "PROSPECTIVE"; card.status = status;
        card.versionNo = 1; card.confidence = .9; card.emotionalGravity = .5; card.visibilityLevel = "PRIVATE";
        card.consentScope = "AURORA_PRIVATE";
        memoryMapper.insert(card); return card;
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean @Primary MemoryEmbeddingClient fakeMemoryEmbeddingClient() {
            return new MemoryEmbeddingClient() {
                public boolean available() { return true; }
                public String modelName() { return "provider-contract-model"; }
                public String modelVersion() { return "v1"; }
                public int dimensions() { return 8; }
                public float[] embed(String text) {
                    boolean relevant = text.contains("deadline") || text.contains("课程报告");
                    return relevant ? new float[]{1,0,0,0,0,0,0,0} : new float[]{0,1,0,0,0,0,0,0};
                }
            };
        }
    }
}
