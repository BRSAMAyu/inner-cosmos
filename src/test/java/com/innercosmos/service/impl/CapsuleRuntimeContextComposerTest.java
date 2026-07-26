package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.CapsuleGenomeVersion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapsuleRuntimeContextComposerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CapsuleRuntimeContextComposer composer = new CapsuleRuntimeContextComposer(objectMapper);

    @Test
    void episodicMemoryOutranksPersonaAndEmitsAuditableManifest() throws Exception {
        Map<String, Object> result = composer.compose(genome(true, true), "遇到冲突时你通常会怎么做？");

        assertEquals("HABIT", result.get("queryIntent"));
        assertEquals("EPISODIC_MEMORY", result.get("groundingLevel"));
        assertFalse((Boolean) result.get("unsupported"));
        assertTrue(String.valueOf(result.get("selectedEvidenceSummary")).contains("#12"));

        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) result.get("contextBuildManifest");
        assertEquals("context-build-manifest.v2", manifest.get("schemaVersion"));
        assertEquals(List.of(12L), manifest.get("selectedMemoryIds"));
        assertEquals("EPISODIC_MEMORY", manifest.get("groundingLevel"));
    }

    @Test
    void relevantConfirmedValueAllowsOnlyPersonaClaimGrounding() throws Exception {
        Map<String, Object> result = composer.compose(genome(false, true), "你最看重什么？");

        assertEquals("PERSONA_CLAIM", result.get("groundingLevel"));
        assertEquals(List.of("VALUE"), result.get("selectedCategories"));
        assertFalse((Boolean) result.get("unsupported"));
        assertTrue(String.valueOf(result.get("selectedContext")).contains("认真回应"));
    }

    @Test
    void expressionStyleCanShapeCurrentReplyButDoesNotGrantSelfDescription() throws Exception {
        Map<String, Object> result = composer.compose(genome(false, true), "跟我说一句晚安吧");

        assertEquals("STYLE_ONLY", result.get("groundingLevel"));
        assertEquals(List.of("EXPRESSION_STYLE"), result.get("selectedCategories"));
        assertFalse((Boolean) result.get("unsupported"));
    }

    @Test
    void unrelatedPersonaFactWithoutStyleRemainsUnsupported() throws Exception {
        Map<String, Object> result = composer.compose(genome(false, false), "你支持哪一支球队？");

        assertEquals("UNSUPPORTED", result.get("groundingLevel"));
        assertEquals(List.of(), result.get("selectedCategories"));
        assertEquals("", result.get("selectedEvidenceSummary"));
        assertTrue((Boolean) result.get("unsupported"));
        assertEquals("ACKNOWLEDGE_UNKNOWN", result.get("fallbackPolicy"));
        assertFalse(String.valueOf(result.get("selectedContext")).contains("认真回应"));
    }

    private CapsuleGenomeVersion genome(boolean includeMemories, boolean includeStyle) throws Exception {
        CapsuleGenomeVersion genome = new CapsuleGenomeVersion();
        genome.id = 7L;
        genome.versionNo = 3;
        genome.compilerVersion = "capsule-genome.v3";
        genome.styleProfileJson = objectMapper.writeValueAsString(Map.of(
                "voice", "克制、具体",
                "calibration", Map.of("toneCodes", List.of("RESTRAINED"))));
        Map<String, Object> ir = includeMemories
                ? Map.of(
                        "claims", List.of(feature("claim-11", "去年搬到成都", 11L)),
                        "values", List.of(feature("value-11", "重视被认真回应", 11L)),
                        "habits", List.of(feature("habit-12", "遇到冲突时通常先冷静再说明边界", 12L)),
                        "temporalState", List.of(),
                        "unknowns", List.of())
                : Map.of("claims", List.of(), "values", List.of(), "habits", List.of(),
                        "temporalState", List.of(), "unknowns", List.of());
        List<Map<String, Object>> persona = includeStyle
                ? List.of(
                        persona(21L, "VALUE", "我看重被认真回应"),
                        persona(22L, "EXPRESSION_STYLE", "表达简短、克制，少用感叹号"))
                : List.of(persona(21L, "VALUE", "我看重被认真回应"));
        genome.contextPreviewJson = objectMapper.writeValueAsString(Map.of(
                "schemaVersion", "capsule-context-preview.v3",
                "genomeIr", ir,
                "personaLayer", persona));
        return genome;
    }

    private Map<String, Object> persona(Long id, String type, String value) {
        return Map.of("claimId", id, "claimType", type, "capsuleSafeValue", value,
                "confidence", 0.9, "evidenceRefs", List.of(100L));
    }

    private Map<String, Object> feature(String id, String statement, Long memoryId) {
        return Map.of(
                "id", id,
                "statement", statement,
                "confidence", 0.9,
                "evidence", List.of(Map.of("memoryId", memoryId, "sourceVersion", 1, "confidence", 0.9)));
    }
}
