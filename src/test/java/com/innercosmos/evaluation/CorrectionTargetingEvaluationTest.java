package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.UserCorrectionService;
import com.innercosmos.vo.CorrectionImpactVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scores how precisely {@link UserCorrectionService#preview} identifies the memories a
 * correction will supersede — the closest honest analogue to "claim precision" available
 * today, since every {@code UnderstandingClaim} originates from an explicit user correction
 * (there is no automatic claim-extraction pipeline yet; see docs/goal/single-session-state.yml).
 * MEMORY_CARD-targeted commands must be perfect (deterministic id match). Free-text
 * AURORA_UNDERSTANDING commands fall back to substring matching and are known to over-match
 * when two memories share a 4+ character phrase — this test measures that real behavior
 * rather than hiding it, and the report is the evidence for why the UI now also offers a
 * star-targeted MEMORY_CARD correction entry point.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:correction-targeting-eval;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class CorrectionTargetingEvaluationTest {
    @Autowired ObjectMapper objectMapper;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired UserCorrectionService correctionService;

    @Test
    void syntheticAnnotatedTargetingGateMeetsPublishedThresholds() throws Exception {
        JsonNode dataset = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/correction-targeting-v1.json"));
        long userId = 92001L;
        Map<String, Long> ids = new HashMap<>();
        for (JsonNode row : dataset.path("memories")) {
            MemoryCard card = new MemoryCard();
            card.userId = userId; card.title = row.path("title").asText(); card.summary = row.path("summary").asText();
            card.memoryType = "COGNITION"; card.memoryLayer = "SEMANTIC"; card.status = "ACTIVE";
            card.versionNo = 1; card.confidence = 0.9; card.emotionalGravity = 1.0;
            memoryMapper.insert(card); ids.put(row.path("key").asText(), card.id);
        }
        Map<Long, String> keysById = new HashMap<>();
        ids.forEach((key, id) -> keysById.put(id, key));

        int memCardTp = 0, memCardFp = 0, memCardFn = 0;
        int overallTp = 0, overallFp = 0, overallFn = 0;
        Map<String, Object> caseReports = new LinkedHashMap<>();
        for (JsonNode testCase : dataset.path("cases")) {
            boolean memoryCardMode = "MEMORY_CARD".equals(testCase.path("mode").asText());
            CorrectionCommand command = memoryCardMode
                    ? new CorrectionCommand("MEMORY_CARD", ids.get(testCase.path("target").asText()),
                        "summary", null, "重新理解这条记忆", "评测夹具")
                    : new CorrectionCommand("AURORA_UNDERSTANDING", 0L, "self_understanding",
                        testCase.path("oldValue").asText(), "重新理解", "评测夹具");
            CorrectionImpactVO impact = correctionService.preview(userId, command);
            Set<String> actual = new HashSet<>();
            for (CorrectionImpactVO.ImpactItem item : impact.impacts()) {
                if ("MEMORY".equals(item.kind()) && item.targetId() != null) actual.add(keysById.get(item.targetId()));
            }
            Set<String> expected = new HashSet<>();
            testCase.path("relevant").forEach(node -> expected.add(node.asText()));

            int tp = 0, fp = 0;
            for (String key : actual) { if (expected.contains(key)) tp++; else fp++; }
            int fn = (int) expected.stream().filter(key -> !actual.contains(key)).count();
            caseReports.put(testCase.path("label").asText(), Map.of("expected", expected, "actual", actual, "tp", tp, "fp", fp, "fn", fn));

            overallTp += tp; overallFp += fp; overallFn += fn;
            if (memoryCardMode) { memCardTp += tp; memCardFp += fp; memCardFn += fn; }
        }

        double memCardPrecision = memCardTp + memCardFp == 0 ? 1.0 : (double) memCardTp / (memCardTp + memCardFp);
        double memCardRecall = memCardTp + memCardFn == 0 ? 1.0 : (double) memCardTp / (memCardTp + memCardFn);
        double overallPrecision = overallTp + overallFp == 0 ? 1.0 : (double) overallTp / (overallTp + overallFp);
        double overallRecall = overallTp + overallFn == 0 ? 1.0 : (double) overallTp / (overallTp + overallFn);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("datasetVersion", dataset.path("datasetVersion").asText());
        report.put("cases", dataset.path("cases").size());
        report.put("memoryCardTargeted", Map.of("precision", memCardPrecision, "recall", memCardRecall));
        report.put("overall", Map.of("precision", overallPrecision, "recall", overallRecall));
        report.put("perCase", caseReports);
        Path reportPath = Path.of("target", "evaluation", "correction-targeting-v1-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        JsonNode thresholds = dataset.path("thresholds");
        assertTrue(memCardPrecision >= thresholds.path("memoryCardTargeted").path("precision").asDouble(), report.toString());
        assertTrue(memCardRecall >= thresholds.path("memoryCardTargeted").path("recall").asDouble(), report.toString());
        assertTrue(overallPrecision >= thresholds.path("overall").path("precision").asDouble(), report.toString());
        assertTrue(overallRecall >= thresholds.path("overall").path("recall").asDouble(), report.toString());
    }
}
