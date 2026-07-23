package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.embedding.DisabledMemoryEmbeddingClient;
import com.innercosmos.ai.embedding.MemoryEmbeddingClient;
import com.innercosmos.ai.embedding.OpenAiCompatibleMemoryEmbeddingClient;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.service.MemoryEmbeddingIndexService;
import com.innercosmos.service.MemoryRetrievalService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W1 / INNO-INNER-012 — REAL embedding provider proof: this is not a mocked HTTP 200 check, it
 * exercises the actual product indexing/retrieval pipeline
 * ({@link MemoryEmbeddingIndexService#rebuildMissing}, {@link MemoryRetrievalService#retrieve})
 * against the real Aliyun DashScope {@code text-embedding-v4} endpoint, over H2 (the "local"
 * scoring path in {@code MemoryEmbeddingIndexServiceImpl}, since this test does not require
 * PostgreSQL/pgvector).
 *
 * <p>Tagged {@code real-provider} and excluded from the default {@code ./mvnw test} gate (see
 * {@code pom.xml}'s surefire {@code excludedGroups}), per the same convention as
 * {@code TrackARealProviderSmokeEvaluationTest}. Reads credentials ONLY from process environment
 * variables (never a file, never logged). Run explicitly, e.g.:
 * <pre>
 *   export MEMORY_EMBEDDING_API_KEY=sk-...
 *   ./mvnw test -Dtest=MemoryEmbeddingRealProviderIndexRetrievalTest -DexcludedGroups=
 * </pre>
 * If {@code MEMORY_EMBEDDING_API_KEY} is absent, the test short-circuits to a
 * {@code SKIPPED_NO_CREDENTIAL} evidence row instead of silently passing.
 */
@Tag("real-provider")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:embedding-real-provider;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
@Import(MemoryEmbeddingRealProviderIndexRetrievalTest.RealEmbeddingConfig.class)
class MemoryEmbeddingRealProviderIndexRetrievalTest {

    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryEmbeddingMapper embeddingMapper;
    @Autowired MemoryRetrievalService retrieval;
    @Autowired MemoryEmbeddingIndexService index;
    @Autowired MemoryEmbeddingClient client;
    @Autowired ObjectMapper objectMapper;

    @Test
    void realEmbeddingRoundTripsThroughIndexingAndRetrieval() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        if (!client.available()) {
            report.put("status", "SKIPPED_NO_CREDENTIAL");
            report.put("note", "MEMORY_EMBEDDING_API_KEY not set in this session's environment; "
                    + "never falls back to a fake/mocked client silently");
            writeReport(report);
            return;
        }

        long owner = 97_001L;
        MemoryCard relevant = card(owner, "提交课程报告", "整理实验结果并交付截止日期前的最终版本", "ACTIVE");
        MemoryCard distractorA = card(owner, "雨天散步", "慢下来观察街道两旁的梧桐树", "ACTIVE");
        MemoryCard distractorB = card(owner, "整理厨房", "清点香料并擦拭橱柜架子", "ACTIVE");

        var rebuilt = index.rebuildMissing(20);
        report.put("status", "CALLED");
        report.put("provider", "aliyun-dashscope");
        report.put("model", client.modelName());
        report.put("modelVersion", client.modelVersion());
        report.put("configuredDimensions", client.dimensions());
        report.put("rebuildIndexed", rebuilt.indexed());
        report.put("rebuildFailed", rebuilt.failed());

        // 1) The real vector actually round-tripped into tb_memory_embedding at the configured width.
        var stored = embeddingMapper.selectList(new QueryWrapper<MemoryEmbedding>().eq("user_id", owner));
        assertEquals(3, stored.size(), report.toString());
        for (MemoryEmbedding row : stored) {
            java.util.List<Double> vector = objectMapper.readValue(row.embeddingJson,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Double>>() {});
            assertEquals(client.dimensions(), vector.size(),
                    "stored embedding_json vector length must equal the real provider's configured dimension: " + report);
        }
        report.put("storedVectorDimension", stored.isEmpty() ? -1
                : objectMapper.readValue(stored.get(0).embeddingJson,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Double>>() {}).size());

        // 2) A semantically related query retrieves the relevant memory over unrelated distractors
        // -- proving the real vectors are not just present but usable for actual retrieval, not
        // merely "the HTTP call returned 200".
        var pack = retrieval.retrieve(owner, new MemoryRetrievalQuery(
                "赶在截止日期前交作业", "ACTION_SPLIT", null, 3, 120, false));
        boolean relevantRetrieved = pack.evidence().stream().anyMatch(e -> e.memoryId().equals(relevant.id));
        report.put("relevantMemoryRetrieved", relevantRetrieved);
        report.put("evidenceCount", pack.evidence().size());
        report.put("distractorIds", java.util.List.of(distractorA.id, distractorB.id));

        writeReport(report);

        assertTrue(relevantRetrieved,
                "real embedding must retrieve the semantically related memory over unrelated distractors: " + report);
    }

    private void writeReport(Map<String, Object> report) throws Exception {
        Path output = Path.of("target", "evaluation", "memory-embedding-real-provider-report.json");
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    private MemoryCard card(long userId, String title, String summary, String status) {
        MemoryCard card = new MemoryCard();
        card.userId = userId; card.title = title; card.summary = summary;
        card.memoryType = "TODO"; card.memoryLayer = "PROSPECTIVE"; card.status = status;
        card.versionNo = 1; card.confidence = .9; card.emotionalGravity = .5; card.visibilityLevel = "PRIVATE";
        card.consentScope = "AURORA_PRIVATE";
        memoryMapper.insert(card);
        return card;
    }

    @TestConfiguration
    static class RealEmbeddingConfig {
        @Bean
        @Primary
        MemoryEmbeddingClient realOrDisabledMemoryEmbeddingClient(ObjectMapper objectMapper) {
            String apiKey = System.getenv("MEMORY_EMBEDDING_API_KEY");
            if (apiKey == null || apiKey.isBlank()) return new DisabledMemoryEmbeddingClient();
            String baseUrl = System.getenv().getOrDefault("MEMORY_EMBEDDING_BASE_URL",
                    "https://dashscope.aliyuncs.com/compatible-mode/v1");
            String model = System.getenv().getOrDefault("MEMORY_EMBEDDING_MODEL", "text-embedding-v4");
            return new OpenAiCompatibleMemoryEmbeddingClient(baseUrl, apiKey, model, "2026-01", 1536, objectMapper);
        }
    }
}
