package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.CapsuleGenomeVersion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the bounded, auditable context used for one PersonaChat turn.
 *
 * <p>The composer consumes an immutable active Genome version, never the mutable capsule draft.
 * It deliberately selects only the IR category supported by the visitor's question. An unfamiliar
 * question receives no unrelated memories and an explicit unknown fallback policy.</p>
 */
@Component
public class CapsuleRuntimeContextComposer {
    private static final String FALLBACK = "ACKNOWLEDGE_UNKNOWN";
    private static final Map<String, List<String>> INTENT_CUES = Map.of(
            "TEMPORAL", List.of("最近", "现在", "目前", "近况", "这段时间", "正在", "today", "recently", "now"),
            "HABIT", List.of("习惯", "通常", "一般会", "总是", "常常", "怎么做", "会怎么", "倾向", "habit", "usually"),
            "VALUE", List.of("重视", "看重", "在意", "原则", "价值", "重要", "believe", "value", "important"),
            "CLAIM", List.of("经历", "发生", "记得", "做过", "去过", "住在", "什么事", "experience", "happened"));
    private static final Map<String, String> CATEGORY_KEY = Map.of(
            "TEMPORAL", "temporalState", "HABIT", "habits", "VALUE", "values", "CLAIM", "claims");

    private final ObjectMapper objectMapper;

    public CapsuleRuntimeContextComposer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> compose(CapsuleGenomeVersion genome, String visitorMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (genome == null || genome.contextPreviewJson == null || genome.contextPreviewJson.isBlank()) {
            return unsupported(result, genome, "NO_ACTIVE_GENOME_IR");
        }

        try {
            JsonNode preview = objectMapper.readTree(genome.contextPreviewJson);
            JsonNode ir = preview.path("genomeIr");
            if (!ir.isObject()) return unsupported(result, genome, "GENOME_IR_MISSING");

            String intent = classify(visitorMessage, ir);
            String category = CATEGORY_KEY.get(intent);
            List<JsonNode> selected = new ArrayList<>();
            if (category != null && ir.path(category).isArray()) {
                String normalizedMessage = visitorMessage == null ? "" : visitorMessage.toLowerCase(Locale.ROOT);
                for (JsonNode feature : ir.path(category)) {
                    if (hasMeaningfulOverlap(normalizedMessage, feature.path("statement").asText(""))) {
                        selected.add(feature);
                    }
                }
                if (selected.size() > 3) selected = new ArrayList<>(selected.subList(0, 3));
            }

            boolean unsupported = selected.isEmpty();
            List<String> categories = unsupported ? List.of() : List.of(category);
            Set<Long> memoryIds = evidenceIds(selected);
            Map<String, Object> selectedContext = new LinkedHashMap<>();
            selectedContext.put("schemaVersion", "capsule-runtime-context.v1");
            selectedContext.put("styleProfile", runtimeStyle(genome.styleProfileJson));
            selectedContext.put("selectedFeatures", selected.stream().map(JsonNode::deepCopy).toList());
            selectedContext.put("unknowns", ir.path("unknowns").isArray()
                    ? objectMapper.convertValue(ir.path("unknowns"), List.class) : List.of());
            selectedContext.put("privacy", preview.path("privacy").asText("只使用当前 Genome 中已授权、带来源的证据"));

            Map<String, Object> manifest = manifest(genome, intent, categories, memoryIds, unsupported,
                    unsupported ? "NO_RELEVANT_GROUNDED_FEATURE" : "CATEGORY_POLICY_MATCH");
            result.put("queryIntent", intent);
            result.put("selectedCategories", categories);
            result.put("selectedEvidenceSummary", evidenceSummary(selected));
            result.put("selectedContext", selectedContext);
            result.put("contextBuildManifest", manifest);
            result.put("unsupported", unsupported);
            result.put("fallbackPolicy", FALLBACK);
            return result;
        } catch (Exception malformedGenome) {
            return unsupported(result, genome, "GENOME_IR_UNREADABLE");
        }
    }

    private String classify(String message, JsonNode ir) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        for (String intent : List.of("TEMPORAL", "HABIT", "VALUE", "CLAIM")) {
            if (INTENT_CUES.get(intent).stream().anyMatch(normalized::contains)) return intent;
        }
        // A cue-free question is allowed to retrieve a claim only when it shares a meaningful
        // two-character/word token with a grounded statement. Otherwise it is unfamiliar.
        for (JsonNode feature : ir.path("claims")) {
            if (hasMeaningfulOverlap(normalized, feature.path("statement").asText(""))) return "CLAIM";
        }
        return "UNFAMILIAR";
    }

    private boolean hasMeaningfulOverlap(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right.toLowerCase(Locale.ROOT));
        leftTokens.retainAll(rightTokens);
        return !leftTokens.isEmpty();
    }

    private Set<String> tokens(String text) {
        String normalized = text.replaceAll("[\\p{P}\\p{S}\\s]+", "");
        Set<String> tokens = new LinkedHashSet<>();
        for (int i = 0; i + 1 < normalized.length(); i++) tokens.add(normalized.substring(i, i + 2));
        for (String word : text.split("[^a-z0-9]+")) if (word.length() >= 3) tokens.add(word);
        return tokens;
    }

    private Set<Long> evidenceIds(List<JsonNode> features) {
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode feature : features) {
            for (JsonNode evidence : feature.path("evidence")) {
                if (evidence.hasNonNull("memoryId")) ids.add(evidence.path("memoryId").asLong());
            }
        }
        return ids;
    }

    private String evidenceSummary(List<JsonNode> features) {
        StringBuilder summary = new StringBuilder();
        for (JsonNode feature : features) {
            Set<Long> ids = evidenceIds(List.of(feature));
            summary.append(ids.stream().map(id -> "#" + id).reduce((a, b) -> a + "," + b).orElse("#unknown"))
                    .append(" ").append(feature.path("statement").asText()).append("\n");
        }
        return summary.toString();
    }

    private Map<String, Object> runtimeStyle(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            JsonNode style = objectMapper.readTree(json);
            Map<String, Object> bounded = new LinkedHashMap<>();
            for (String key : List.of("voice", "dominantSentiment", "confidence", "notBeautified", "boundary")) {
                if (style.has(key)) bounded.put(key, objectMapper.convertValue(style.get(key), Object.class));
            }
            return bounded;
        }
        catch (Exception ignored) { return Map.of("unreadable", true); }
    }

    private Map<String, Object> unsupported(Map<String, Object> result, CapsuleGenomeVersion genome, String reason) {
        Map<String, Object> context = Map.of(
                "schemaVersion", "capsule-runtime-context.v1",
                "selectedFeatures", List.of(),
                "unknowns", List.of(reason));
        result.put("queryIntent", "UNFAMILIAR");
        result.put("selectedCategories", List.of());
        result.put("selectedEvidenceSummary", "");
        result.put("selectedContext", context);
        result.put("contextBuildManifest", manifest(genome, "UNFAMILIAR", List.of(), Set.of(), true, reason));
        result.put("unsupported", true);
        result.put("fallbackPolicy", FALLBACK);
        return result;
    }

    private Map<String, Object> manifest(CapsuleGenomeVersion genome, String intent,
                                         List<String> categories, Set<Long> ids,
                                         boolean unsupported, String reason) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", "context-build-manifest.v1");
        manifest.put("genomeVersionId", genome == null ? null : genome.id);
        manifest.put("genomeVersionNo", genome == null ? null : genome.versionNo);
        manifest.put("compilerVersion", genome == null ? null : genome.compilerVersion);
        manifest.put("queryIntent", intent);
        manifest.put("selectedCategories", categories);
        manifest.put("selectedMemoryIds", new ArrayList<>(ids));
        manifest.put("unsupported", unsupported);
        manifest.put("selectionReason", reason);
        manifest.put("fallbackPolicy", FALLBACK);
        return manifest;
    }
}
