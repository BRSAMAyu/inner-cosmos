package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.CapsuleGenomeVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CapsuleRuntimeContextComposerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CapsuleRuntimeContextComposer composer = new CapsuleRuntimeContextComposer(objectMapper);

    @Test
    void habitQuestionSelectsOnlyHabitEvidenceAndEmitsAuditableManifest() throws Exception {
        Map<String, Object> result = composer.compose(genome(), "遇到冲突时你通常会怎么做？");

        assertEquals("HABIT", result.get("queryIntent"));
        assertEquals(List.of("habits"), result.get("selectedCategories"));
        assertFalse((Boolean) result.get("unsupported"));
        assertTrue(String.valueOf(result.get("selectedEvidenceSummary")).contains("#12"));
        assertFalse(String.valueOf(result.get("selectedEvidenceSummary")).contains("#11"));

        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) result.get("contextBuildManifest");
        assertEquals("context-build-manifest.v1", manifest.get("schemaVersion"));
        assertEquals(7L, manifest.get("genomeVersionId"));
        assertEquals("capsule-genome.v3", manifest.get("compilerVersion"));
        assertEquals(List.of(12L), manifest.get("selectedMemoryIds"));
        assertFalse(String.valueOf(result.get("selectedContext")).contains("99"),
                "style evidence outside the manifest must not ride along with the selected feature");
    }

    @Test
    void unfamiliarQuestionDoesNotDumpUnrelatedGenomeAndRequiresUnknownAcknowledgement() throws Exception {
        Map<String, Object> result = composer.compose(genome(), "你支持哪一支球队？");

        assertEquals("UNFAMILIAR", result.get("queryIntent"));
        assertEquals(List.of(), result.get("selectedCategories"));
        assertEquals("", result.get("selectedEvidenceSummary"));
        assertTrue((Boolean) result.get("unsupported"));
        assertEquals("ACKNOWLEDGE_UNKNOWN", result.get("fallbackPolicy"));
        assertFalse(String.valueOf(result.get("selectedContext")).contains("搬到成都"),
                "an unfamiliar question must not receive unrelated claims just because they exist");
    }

    private CapsuleGenomeVersion genome() throws Exception {
        CapsuleGenomeVersion genome = new CapsuleGenomeVersion();
        genome.id = 7L;
        genome.versionNo = 3;
        genome.compilerVersion = "capsule-genome.v3";
        genome.styleProfileJson = objectMapper.writeValueAsString(Map.of(
                "voice", "克制、具体", "voiceEvidence", Map.of("未选择主题", List.of(99L))));
        genome.contextPreviewJson = objectMapper.writeValueAsString(Map.of(
                "schemaVersion", "capsule-context-preview.v3",
                "genomeIr", Map.of(
                        "schemaVersion", "capsule-genome-ir.v1",
                        "claims", List.of(feature("claim-11", "去年搬到成都", 11L)),
                        "values", List.of(feature("value-11", "重视被认真回应", 11L)),
                        "habits", List.of(feature("habit-12", "遇到冲突时通常先冷静再说明边界", 12L)),
                        "temporalState", List.of(feature("temporal-13", "最近正在准备展示", 13L)),
                        "unknowns", List.of()),
                "scenes", List.of(),
                "retrievalPolicy", Map.of("unsupportedBehavior", "ACKNOWLEDGE_UNKNOWN")));
        return genome;
    }

    private Map<String, Object> feature(String id, String statement, Long memoryId) {
        return Map.of(
                "id", id,
                "statement", statement,
                "confidence", 0.9,
                "evidence", List.of(Map.of("memoryId", memoryId, "sourceVersion", 1, "confidence", 0.9)));
    }
}
