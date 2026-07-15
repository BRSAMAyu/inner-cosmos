package com.innercosmos.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.service.MemoryEmbeddingIndexService;
import com.innercosmos.ai.embedding.DisabledMemoryEmbeddingClient;
import com.innercosmos.service.impl.MemoryEmbeddingIndexServiceImpl;
import com.innercosmos.service.impl.MemoryRetrievalServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:embedding-candidate;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
@Import(MemoryEmbeddingCandidateIntegrationTest.FakeEmbeddingConfig.class)
class MemoryEmbeddingCandidateIntegrationTest {
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryEmbeddingMapper embeddingMapper;
    @Autowired MemoryRetrievalService retrieval;
    @Autowired MemoryEmbeddingIndexService index;
    @Autowired FakeMemoryEmbeddingClient fakeClient;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void providerCandidateFindsParaphraseWithoutCrossingOwnerOrStatusGates() {
        long owner = 92001L;
        MemoryCard relevant = card(owner, "提交课程报告", "整理实验结果并交付", "ACTIVE");
        card(owner, "雨天散步", "慢下来观察街道", "ACTIVE");
        card(owner, "旧课程判断", "已经被纠正", "CONTRADICTED");
        MemoryCard localOnly = card(owner, "本地私密课程记录", "不得发送给外部 embedding provider", "ACTIVE");
        localOnly.consentScope = "local_only"; memoryMapper.updateById(localOnly);
        card(92002L, "提交课程报告", "另一个用户的内容", "ACTIVE");

        var rebuilt = index.rebuildMissing(20);
        assertTrue(rebuilt.indexed() >= 3);
        assertEquals(0, rebuilt.failed());

        int callsAfterBackgroundRebuild = fakeClient.calls.get();
        var pack = retrieval.retrieve(owner, new MemoryRetrievalQuery(
                "deadline deliverable", "ACTION_SPLIT", null, 3, 120, false));
        assertEquals(callsAfterBackgroundRebuild + 1, fakeClient.calls.get(),
                "online retrieval may embed the query once but must not embed every document");
        assertEquals(relevant.id, pack.evidence().get(0).memoryId());
        assertEquals(2, embeddingMapper.selectCount(new QueryWrapper<MemoryEmbedding>().eq("user_id", owner)));
        assertEquals("provider-contract-model", embeddingMapper.selectList(
                new QueryWrapper<MemoryEmbedding>().eq("user_id", owner)).get(0).modelName);
        assertEquals(0, index.rebuildMissing(20).selected(), "a current model/version must not be rebuilt");
    }

    @Test
    void providerIndexBeatsLocalBaselineOnHardParaphraseTemporalAndMultiHopCases() throws Exception {
        long owner = 93001L;
        // Distractors deliberately precede the answers so the no-overlap local baseline
        // cannot win from stable database order alone.
        card(owner, "周末整理厨房", "清点香料并擦拭架子", "ACTIVE");
        card(owner, "雨后看电影", "一个人在家看旧片", "ACTIVE");
        card(owner, "给植物换盆", "准备新的土和陶盆", "ACTIVE");
        MemoryCard boundary = card(owner, "需要先留一点空间", "难受时请先陪我，不要立刻给建议", "ACTIVE");
        MemoryCard passport = card(owner, "护照将在秋天前到期", "出行前要完成证件续期", "ACTIVE");
        MemoryCard alanHistory = card(owner, "阿岚是大学室友", "我们一起完成过毕业项目", "ACTIVE");
        MemoryCard alanNow = card(owner, "阿岚搬到了新加坡", "现在住得很近，可以约散步", "ACTIVE");
        MemoryCard currentRoutine = card(owner, "现在更喜欢夜里写作", "晚上安静时更容易进入创作状态", "ACTIVE");
        MemoryCard staleRoutine = card(owner, "我总是清晨写作", "这是已经改变的旧习惯", "CONTRADICTED");

        int callsBeforeRebuild = fakeClient.calls.get();
        index.rebuildMissing(100);
        int documentCalls = fakeClient.calls.get() - callsBeforeRebuild;
        MemoryEmbeddingIndexService localIndex = new MemoryEmbeddingIndexServiceImpl(
                new DisabledMemoryEmbeddingClient(), embeddingMapper, memoryMapper, jdbc, objectMapper);
        MemoryRetrievalService local = new MemoryRetrievalServiceImpl(memoryMapper, localIndex);
        List<PairwiseCase> cases = List.of(
                new PairwiseCase("space before advice", "AURORA_CONVERSATION", Set.of(boundary.id)),
                new PairwiseCase("travel document admin", "ACTION_SPLIT", Set.of(passport.id)),
                new PairwiseCase("which old classmate is nearby", "RELATION_REVIEW", Set.of(alanHistory.id, alanNow.id)),
                new PairwiseCase("current creative routine", "PROFILE_REVIEW", Set.of(currentRoutine.id)));

        long localStarted = System.nanoTime();
        double localRecall = recallAt3(local, owner, cases);
        double localMillis = (System.nanoTime() - localStarted) / 1_000_000.0;
        int callsBeforeQueries = fakeClient.calls.get();
        long providerStarted = System.nanoTime();
        double providerRecall = recallAt3(retrieval, owner, cases);
        double providerMillis = (System.nanoTime() - providerStarted) / 1_000_000.0;
        boolean staleReturned = retrieval.retrieve(owner,
                new MemoryRetrievalQuery("current creative routine", "PROFILE_REVIEW", null, 3, 120, false))
                .evidence().stream().anyMatch(row -> row.memoryId().equals(staleRoutine.id));
        int queryCalls = fakeClient.calls.get() - callsBeforeQueries;
        Map<String, Object> report = Map.ofEntries(
                Map.entry("datasetVersion", "memory-retrieval-hard-v2"),
                Map.entry("cases", cases.size()),
                Map.entry("localRecallAt3", localRecall),
                Map.entry("providerRecallAt3", providerRecall),
                Map.entry("absoluteLift", providerRecall - localRecall),
                Map.entry("localTotalMillis", localMillis),
                Map.entry("providerContractTotalMillis", providerMillis),
                Map.entry("providerDocumentCallsInBackgroundBatch", documentCalls),
                Map.entry("providerOnlineQueryCalls", queryCalls),
                Map.entry("pricing", "not measured: deterministic local contract provider"),
                Map.entry("prohibitedCurrentRoutineReturned", staleReturned));
        Path output = Path.of("target", "evaluation", "memory-retrieval-provider-pairwise-report.json");
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);

        assertEquals(1.0, providerRecall, 0.0001, report.toString());
        assertTrue(providerRecall - localRecall >= 0.30, report.toString());
        assertEquals(false, report.get("prohibitedCurrentRoutineReturned"), report.toString());
    }

    private static double recallAt3(MemoryRetrievalService service, long owner, List<PairwiseCase> cases) {
        double total = 0;
        for (PairwiseCase testCase : cases) {
            var result = service.retrieve(owner, new MemoryRetrievalQuery(
                    testCase.query, testCase.task, null, 3, 120, false));
            Set<Long> actual = result.evidence().stream().map(row -> row.memoryId()).collect(java.util.stream.Collectors.toSet());
            total += (double) testCase.relevant.stream().filter(actual::contains).count() / testCase.relevant.size();
        }
        return total / cases.size();
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
        @Bean @Primary FakeMemoryEmbeddingClient fakeMemoryEmbeddingClient() {
            return new FakeMemoryEmbeddingClient();
        }
    }

    static class FakeMemoryEmbeddingClient implements MemoryEmbeddingClient {
        final AtomicInteger calls = new AtomicInteger();
        public boolean available() { return true; }
        public String modelName() { return "provider-contract-model"; }
        public String modelVersion() { return "v1"; }
        public int dimensions() { return 8; }
        public float[] embed(String text) {
            calls.incrementAndGet();
            int group = semanticGroup(text);
            float[] vector = new float[8]; vector[group] = 1; return vector;
        }

        private static int semanticGroup(String text) {
            if (text.contains("deadline") || text.contains("课程报告")) return 0;
            if (text.contains("space before advice") || text.contains("不要立刻给建议")) return 1;
            if (text.contains("travel document") || text.contains("护照将在秋天前到期")) return 2;
            if (text.contains("old classmate") || text.contains("阿岚")) return 3;
            if (text.contains("current creative routine") || text.contains("夜里写作")) return 4;
            return 7;
        }
    }

    private record PairwiseCase(String query, String task, Set<Long> relevant) {}
}
