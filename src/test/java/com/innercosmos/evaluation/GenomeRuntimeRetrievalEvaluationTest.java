package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.service.impl.CapsuleRuntimeContextComposer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Machine evaluation for intent routing, evidence isolation, and unfamiliar-scenario fallback. */
class GenomeRuntimeRetrievalEvaluationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void annotatedScenariosSelectOnlyExpectedEvidenceAndRefuseUnfamiliarClaims() throws Exception {
        JsonNode dataset = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/genome-runtime-retrieval-v1.json"));
        CapsuleRuntimeContextComposer composer = new CapsuleRuntimeContextComposer(objectMapper);
        CapsuleGenomeVersion genome = genome(dataset.path("features"));

        int intentCorrect = 0;
        int selectionCorrect = 0;
        int evidenceLeakCount = 0;
        int unsupportedCorrect = 0;
        int unsupportedCases = 0;
        List<Map<String, Object>> cases = new ArrayList<>();
        for (JsonNode scenario : dataset.path("scenarios")) {
            Map<String, Object> result = composer.compose(genome, scenario.path("question").asText());
            boolean intentMatch = scenario.path("expectedIntent").asText().equals(result.get("queryIntent"));
            if (intentMatch) intentCorrect++;

            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = (Map<String, Object>) result.get("contextBuildManifest");
            Set<Long> actualIds = new LinkedHashSet<>();
            for (Object value : (List<?>) manifest.get("selectedMemoryIds")) {
                actualIds.add(((Number) value).longValue());
            }
            Set<Long> expectedIds = new LinkedHashSet<>();
            scenario.path("expectedMemoryIds").forEach(id -> expectedIds.add(id.asLong()));
            if (!expectedIds.containsAll(actualIds)) evidenceLeakCount += actualIds.size();
            @SuppressWarnings("unchecked")
            List<String> selectedCategories = (List<String>) result.get("selectedCategories");
            String expectedCategory = scenario.path("expectedCategory").asText();
            boolean categoryMatch = expectedCategory.isBlank()
                    ? selectedCategories.isEmpty() : selectedCategories.equals(List.of(expectedCategory));
            boolean selectionMatch = expectedIds.equals(actualIds) && categoryMatch;
            if (selectionMatch) selectionCorrect++;

            boolean expectedUnsupported = scenario.path("unsupported").asBoolean();
            if (expectedUnsupported) {
                unsupportedCases++;
                if (Boolean.TRUE.equals(result.get("unsupported")) && actualIds.isEmpty()
                        && "ACKNOWLEDGE_UNKNOWN".equals(result.get("fallbackPolicy"))) {
                    unsupportedCorrect++;
                }
            }
            cases.add(Map.of(
                    "key", scenario.path("key").asText(),
                    "intentMatch", intentMatch,
                    "selectionMatch", selectionMatch,
                    "expectedMemoryIds", expectedIds,
                    "selectedMemoryIds", actualIds,
                    "unsupported", result.get("unsupported")));
        }

        double intentAccuracy = intentCorrect / (double) dataset.path("scenarios").size();
        double selectionAccuracy = selectionCorrect / (double) dataset.path("scenarios").size();
        double unsupportedAccuracy = unsupportedCorrect / (double) unsupportedCases;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("datasetVersion", dataset.path("datasetVersion").asText());
        report.put("scenarioCount", dataset.path("scenarios").size());
        report.put("intentAccuracy", intentAccuracy);
        report.put("selectionAccuracy", selectionAccuracy);
        report.put("evidenceLeakCount", evidenceLeakCount);
        report.put("unsupportedFallbackAccuracy", unsupportedAccuracy);
        report.put("cases", cases);
        Path reportPath = Path.of("target", "evaluation", "genome-runtime-retrieval-v1-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        JsonNode thresholds = dataset.path("thresholds");
        assertTrue(intentAccuracy >= thresholds.path("intentAccuracy").asDouble(), report.toString());
        assertTrue(selectionAccuracy >= thresholds.path("selectionAccuracy").asDouble(), report.toString());
        assertTrue(evidenceLeakCount <= thresholds.path("evidenceLeakCount").asInt(), report.toString());
        assertTrue(unsupportedAccuracy >= thresholds.path("unsupportedFallbackAccuracy").asDouble(), report.toString());
    }

    private CapsuleGenomeVersion genome(JsonNode features) throws Exception {
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("schemaVersion", "capsule-genome-ir.v1");
        for (String category : List.of("claims", "values", "habits", "temporalState")) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode row : features.path(category)) {
                rows.add(Map.of(
                        "id", row.path("id").asText(),
                        "statement", row.path("statement").asText(),
                        "confidence", 0.9,
                        "evidence", List.of(Map.of("memoryId", row.path("memoryId").asLong(),
                                "sourceVersion", 1, "confidence", 0.9))));
            }
            ir.put(category, rows);
        }
        ir.put("unknowns", List.of());
        CapsuleGenomeVersion genome = new CapsuleGenomeVersion();
        genome.id = 77L;
        genome.versionNo = 3;
        genome.compilerVersion = "capsule-genome.v3";
        genome.styleProfileJson = "{\"voice\":\"克制、具体\"}";
        genome.contextPreviewJson = objectMapper.writeValueAsString(Map.of(
                "schemaVersion", "capsule-context-preview.v3", "genomeIr", ir,
                "privacy", "authorized only"));
        return genome;
    }
}
