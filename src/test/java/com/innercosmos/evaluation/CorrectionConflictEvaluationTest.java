package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.service.UserCorrectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Scores whether sequences of confirmed and retracted corrections on the same claimKey
 * always resolve to exactly one ACTIVE claim with the functionally correct value — never
 * zero, never more than one. This is the fourth and last of the doc-16-mandated Campaign B
 * eval dimensions (claim/targeting precision, retrieval relevance, conflict handling,
 * forgetting completeness); the other three are already covered by
 * CorrectionTargetingEvaluationTest, MemoryRetrievalEvaluationTest and
 * ForgettingCompletenessEvaluationTest. Existing JUnit coverage
 * (UserCorrectionControllerTest.retiringLatestCorrectionRestoresPreviousClaimAndKeepsAuditHistory)
 * only asserts one hand-picked sequence structurally; this scores several interleavings
 * — including retracting an *older*, already-superseded correction, which the plain JUnit
 * suite does not cover — against a labeled expectation of the resulting claim distribution.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:correction-conflict-eval;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class CorrectionConflictEvaluationTest {
    @Autowired ObjectMapper objectMapper;
    @Autowired UserCorrectionService correctionService;

    @Test
    void syntheticAnnotatedConflictGateResolvesToExactlyOneActiveClaim() throws Exception {
        JsonNode dataset = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/correction-conflict-v1.json"));
        long userId = 94001L;
        int violations = 0;
        Map<String, Object> caseReports = new LinkedHashMap<>();

        for (JsonNode scenario : dataset.path("scenarios")) {
            String key = scenario.path("key").asText();
            String fieldName = scenario.path("fieldName").asText();
            String claimKey = "AURORA_UNDERSTANDING:0:" + fieldName;
            List<Long> confirmedCorrectionIds = new ArrayList<>();

            for (JsonNode step : scenario.path("steps")) {
                if ("CONFIRM".equals(step.path("op").asText())) {
                    var confirmation = correctionService.confirm(userId, new CorrectionCommand(
                            "AURORA_UNDERSTANDING", 0L, fieldName, null, step.path("value").asText(), "评测夹具"));
                    confirmedCorrectionIds.add(confirmation.correction().id);
                } else if ("RETRACT".equals(step.path("op").asText())) {
                    int position = step.path("retract").asInt();
                    correctionService.deleteCorrection(userId, confirmedCorrectionIds.get(position - 1));
                }
            }

            List<UnderstandingClaim> history = correctionService.claimHistory(userId, claimKey);
            long activeCount = history.stream().filter(c -> "ACTIVE".equals(c.status)).count();
            long supersededCount = history.stream().filter(c -> "SUPERSEDED".equals(c.status)).count();
            long retiredCount = history.stream().filter(c -> "RETIRED".equals(c.status)).count();
            String activeValue = history.stream().filter(c -> "ACTIVE".equals(c.status)).findFirst()
                    .map(c -> c.valueJson.replaceAll("^\"|\"$", "")).orElse(null);

            JsonNode expected = scenario.path("expected");
            boolean ok = activeCount == expected.path("active").asLong()
                    && supersededCount == expected.path("superseded").asLong()
                    && retiredCount == expected.path("retired").asLong()
                    && expected.path("activeValue").asText().equals(activeValue);
            if (!ok) violations++;
            caseReports.put(key, Map.of(
                    "active", activeCount, "superseded", supersededCount, "retired", retiredCount,
                    "activeValue", String.valueOf(activeValue), "expected", objectMapper.convertValue(expected, Map.class),
                    "ok", ok));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("datasetVersion", dataset.path("datasetVersion").asText());
        report.put("scenarios", dataset.path("scenarios").size());
        report.put("violations", violations);
        report.put("perCase", caseReports);
        Path reportPath = Path.of("target", "evaluation", "correction-conflict-v1-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        assertEquals(dataset.path("thresholds").path("violations").asInt(), violations, report.toString());
    }
}
