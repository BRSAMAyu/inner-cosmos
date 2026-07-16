package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.claim.ClaimCandidate;
import com.innercosmos.ai.claim.ClaimCandidateExtractor;
import com.innercosmos.ai.claim.ClaimTypes;
import com.innercosmos.entity.DialogMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Campaign B — automatic claim-extraction precision gate over an annotated conversation dataset.
 *
 * <p>This is the machine-verifiable floor doc-16 asks for ("以标注集评测 claim precision"): given the
 * user's own utterances, the deterministic extractor must propose typed claim candidates with correct
 * type, value and provenance, and must NOT fabricate claims from questions, hypotheticals, reported
 * speech or momentary feelings. Precision is weighted above recall. Real-provider extraction quality
 * remains the separate human gate (REAL-PROVIDER-CREDENTIALS-AND-BLIND-REVIEW).
 *
 * <p>Runs pure (no Spring context): the extractor is provider-independent, so the gate proves the
 * deterministic engine directly rather than mock-LLM plumbing.
 */
class ClaimExtractionEvaluationTest {

    @Test
    void syntheticAnnotatedExtractionGateMeetsPublishedThresholds() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode dataset = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/claim-extraction-v1.json"));

        int tp = 0, fp = 0, fn = 0, typeCorrect = 0, typeTotal = 0;
        Map<String, Object> caseReports = new LinkedHashMap<>();

        for (JsonNode testCase : dataset.path("cases")) {
            List<DialogMessage> messages = new ArrayList<>();
            for (JsonNode m : testCase.path("messages")) {
                DialogMessage dm = new DialogMessage();
                dm.id = m.path("id").asLong();
                dm.userId = 88001L;
                dm.speaker = m.path("speaker").asText();
                dm.textContent = m.path("text").asText();
                messages.add(dm);
            }

            List<ClaimCandidate> produced = ClaimCandidateExtractor.extract(messages);
            List<JsonNode> expected = new ArrayList<>();
            testCase.path("expected").forEach(expected::add);

            boolean[] expectedMatched = new boolean[expected.size()];
            int caseTp = 0, caseFp = 0;
            List<String> producedSummary = new ArrayList<>();
            for (ClaimCandidate candidate : produced) {
                producedSummary.add(candidate.claimType() + "=" + candidate.value()
                        + " ids=" + candidate.provenanceMessageIds());
                int matchIdx = -1;
                for (int i = 0; i < expected.size(); i++) {
                    if (expectedMatched[i]) continue;
                    if (matches(candidate, expected.get(i))) {
                        matchIdx = i;
                        break;
                    }
                }
                if (matchIdx >= 0) {
                    expectedMatched[matchIdx] = true;
                    caseTp++;
                    typeTotal++;
                    if (expected.get(matchIdx).path("claimType").asText().equals(candidate.claimType())) {
                        typeCorrect++;
                    }
                } else {
                    caseFp++;
                }
            }
            int caseFn = 0;
            for (boolean matched : expectedMatched) if (!matched) caseFn++;
            tp += caseTp; fp += caseFp; fn += caseFn;
            caseReports.put(testCase.path("label").asText(),
                    Map.of("expected", expected.size(), "produced", producedSummary, "tp", caseTp, "fp", caseFp, "fn", caseFn));
        }

        double precision = tp + fp == 0 ? 1.0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 1.0 : (double) tp / (tp + fn);
        double typeAccuracy = typeTotal == 0 ? 1.0 : (double) typeCorrect / typeTotal;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("datasetVersion", dataset.path("datasetVersion").asText());
        report.put("cases", dataset.path("cases").size());
        report.put("precision", precision);
        report.put("recall", recall);
        report.put("typeAccuracy", typeAccuracy);
        report.put("tp", tp); report.put("fp", fp); report.put("fn", fn);
        report.put("perCase", caseReports);
        Path reportPath = Path.of("target", "evaluation", "claim-extraction-v1-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        JsonNode thresholds = dataset.path("thresholds");
        assertTrue(precision >= thresholds.path("precision").asDouble(),
                "precision " + precision + " < threshold; report=" + report);
        assertTrue(recall >= thresholds.path("recall").asDouble(),
                "recall " + recall + " < threshold; report=" + report);
        assertTrue(typeAccuracy >= thresholds.path("typeAccuracy").asDouble(),
                "typeAccuracy " + typeAccuracy + " < threshold; report=" + report);
    }

    /** A candidate matches an expected row on claimType + a valueContains substring + provenance overlap. */
    private boolean matches(ClaimCandidate candidate, JsonNode expected) {
        if (!expected.path("claimType").asText().equals(candidate.claimType())) return false;
        if (!ClaimTypes.ALL.contains(candidate.claimType())) return false;
        String needle = expected.path("valueContains").asText("");
        if (!needle.isEmpty() && (candidate.value() == null || !candidate.value().contains(needle))) return false;
        List<Long> expectedIds = new ArrayList<>();
        expected.path("provenanceIds").forEach(n -> expectedIds.add(n.asLong()));
        if (!expectedIds.isEmpty()) {
            boolean overlap = candidate.provenanceMessageIds() != null
                    && candidate.provenanceMessageIds().stream().anyMatch(expectedIds::contains);
            if (!overlap) return false;
        }
        return true;
    }
}
