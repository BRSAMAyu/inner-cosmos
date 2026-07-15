package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:retrieval-eval;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class MemoryRetrievalEvaluationTest {
    @Autowired ObjectMapper objectMapper;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryRetrievalService retrievalService;

    @Test
    void syntheticAnnotatedRetrievalGateMeetsPublishedThresholds() throws Exception {
        JsonNode dataset = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/memory-retrieval-v1.json"));
        long userId = 91001L;
        Map<String, Long> ids = new HashMap<>();
        for (JsonNode row : dataset.path("memories")) {
            MemoryCard card = new MemoryCard();
            card.userId = userId; card.title = row.path("title").asText(); card.summary = row.path("summary").asText();
            card.memoryType = row.path("type").asText(); card.memoryLayer = row.path("layer").asText();
            card.status = row.path("status").asText(); card.versionNo = 1; card.confidence = 0.9;
            card.emotionalGravity = 1.0; card.visibilityLevel = "PRIVATE"; card.consentScope = "AURORA_PRIVATE";
            memoryMapper.insert(card); ids.put(row.path("key").asText(), card.id);
        }
        MemoryCard foreign = new MemoryCard(); foreign.userId = 91002L; foreign.title = "小林 独处 边界";
        foreign.summary = "其他用户的私密记录"; foreign.memoryType = "RELATION"; foreign.memoryLayer = "RELATIONAL";
        foreign.status = "ACTIVE"; foreign.versionNo = 1; memoryMapper.insert(foreign);

        double recallSum = 0, reciprocalRankSum = 0;
        int rankedCases = 0, relevantTotal = 0, relevantFound = 0, prohibitedLeakage = 0, budgetViolations = 0;
        List<Double> latencies = new ArrayList<>();
        for (JsonNode testCase : dataset.path("cases")) {
            long started = System.nanoTime();
            var pack = retrievalService.retrieve(userId, new MemoryRetrievalQuery(
                    testCase.path("query").asText(), testCase.path("task").asText(), null, 3, 120, false));
            latencies.add((System.nanoTime() - started) / 1_000_000.0);
            List<Long> actual = pack.evidence().stream().map(evidence -> evidence.memoryId()).toList();
            Set<Long> relevant = keys(testCase.path("relevant"), ids);
            Set<Long> prohibited = keys(testCase.path("prohibited"), ids);
            relevantTotal += relevant.size();
            relevantFound += relevant.stream().filter(actual::contains).count();
            prohibitedLeakage += prohibited.stream().filter(actual::contains).count();
            if (pack.estimatedTokens() > pack.tokenBudget()) budgetViolations++;
            assertTrue(actual.stream().noneMatch(id -> id.equals(foreign.id)), "cross-user memory leaked");
            if (!relevant.isEmpty()) {
                rankedCases++;
                long found = relevant.stream().filter(actual::contains).count();
                recallSum += (double) found / relevant.size();
                for (int i = 0; i < actual.size(); i++) if (relevant.contains(actual.get(i))) {
                    reciprocalRankSum += 1.0 / (i + 1); break;
                }
            }
        }
        latencies.sort(Double::compareTo);
        double macroRecallAt3 = recallSum / rankedCases;
        double microRecallAt3 = relevantTotal == 0 ? 1 : (double) relevantFound / relevantTotal;
        double mrr = reciprocalRankSum / rankedCases;
        double p95 = latencies.get((int) Math.ceil(latencies.size() * .95) - 1);
        JsonNode thresholds = dataset.path("thresholds");
        Map<String, Object> report = Map.of(
                "datasetVersion", dataset.path("datasetVersion").asText(), "cases", dataset.path("cases").size(),
                "macroRecallAt3", macroRecallAt3, "microRecallAt3", microRecallAt3, "mrr", mrr,
                "prohibitedLeakage", prohibitedLeakage, "budgetViolations", budgetViolations, "p95Millis", p95);
        Path reportPath = Path.of("target", "evaluation", "memory-retrieval-v1-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        assertTrue(macroRecallAt3 >= thresholds.path("recallAt3").asDouble(), report.toString());
        assertTrue(mrr >= thresholds.path("mrr").asDouble(), report.toString());
        assertEquals(thresholds.path("prohibitedLeakage").asInt(), prohibitedLeakage, report.toString());
        assertEquals(thresholds.path("budgetViolations").asInt(), budgetViolations, report.toString());
        assertTrue(p95 <= thresholds.path("p95Millis").asDouble(), report.toString());
    }

    private static Set<Long> keys(JsonNode values, Map<String, Long> ids) {
        Set<Long> result = new HashSet<>();
        values.forEach(value -> result.add(ids.get(value.asText()))); return result;
    }
}
