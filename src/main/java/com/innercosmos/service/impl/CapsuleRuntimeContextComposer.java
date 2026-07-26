package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.CapsuleGenomeVersion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Builds the bounded, auditable context used for one persona-chat turn. */
@Component
public class CapsuleRuntimeContextComposer {
    private static final String FALLBACK = "ACKNOWLEDGE_UNKNOWN";
    private static final Map<String, List<String>> INTENT_CUES = Map.of(
            "TEMPORAL", List.of("最近", "现在", "目前", "近况", "这段时间", "today", "recently", "now"),
            "HABIT", List.of("习惯", "通常", "一般会", "总是", "常常", "倾向", "habit", "usually"),
            "VALUE", List.of("重视", "看重", "在意", "原则", "价值", "重要", "believe", "value", "important"),
            "CLAIM", List.of("经历", "发生", "记得", "做过", "去过", "住在", "experience", "happened"));
    private static final Map<String, String> CATEGORY_KEY = Map.of(
            "TEMPORAL", "temporalState", "HABIT", "habits", "VALUE", "values", "CLAIM", "claims");
    private static final Set<String> SELF_DESCRIPTION_TYPES =
            Set.of("VALUE", "PREFERENCE", "NEED", "EMOTION_PATTERN");
    private static final Set<String> STYLE_TYPES = Set.of("EXPRESSION_STYLE", "BOUNDARY");

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
            if (!ir.isObject()) ir = objectMapper.createObjectNode();

            String intent = classify(visitorMessage, ir);
            String category = CATEGORY_KEY.get(intent);
            List<JsonNode> selectedMemories = selectMemories(ir, category, visitorMessage);
            List<JsonNode> personaClaims = selectPersonaClaims(preview.path("personaLayer"), visitorMessage);
            boolean hasSelfDescription = personaClaims.stream().anyMatch(this::isSelfDescriptionClaim);
            boolean hasStyle = personaClaims.stream().anyMatch(this::isStyleClaim);

            String groundingLevel;
            if (!selectedMemories.isEmpty()) groundingLevel = "EPISODIC_MEMORY";
            else if (hasSelfDescription) groundingLevel = "PERSONA_CLAIM";
            else if (hasStyle) groundingLevel = "STYLE_ONLY";
            else groundingLevel = "UNSUPPORTED";

            boolean unsupported = "UNSUPPORTED".equals(groundingLevel);
            List<String> categories = !selectedMemories.isEmpty() ? List.of(category)
                    : personaClaims.stream().map(node -> node.path("claimType").asText())
                            .distinct().toList();
            Set<Long> memoryIds = evidenceIds(selectedMemories);
            Set<Long> claimIds = claimIds(personaClaims);

            Map<String, Object> selectedContext = new LinkedHashMap<>();
            selectedContext.put("schemaVersion", "capsule-runtime-context.v1");
            selectedContext.put("styleProfile", runtimeStyle(genome.styleProfileJson));
            selectedContext.put("selectedFeatures",
                    selectedMemories.stream().map(JsonNode::deepCopy).toList());
            selectedContext.put("selectedPersonaClaims",
                    personaClaims.stream().map(JsonNode::deepCopy).toList());
            selectedContext.put("unknowns", ir.path("unknowns").isArray()
                    ? objectMapper.convertValue(ir.path("unknowns"), List.class) : List.of());
            selectedContext.put("privacy", preview.path("privacy").asText(
                    "Only evidence authorized into this immutable Genome may be used."));

            result.put("queryIntent", intent);
            result.put("selectedCategories", categories);
            result.put("selectedEvidenceSummary", evidenceSummary(selectedMemories));
            result.put("selectedContext", selectedContext);
            result.put("contextBuildManifest", manifest(genome, intent, categories, memoryIds, claimIds,
                    groundingLevel, unsupported, unsupported
                            ? "NO_RELEVANT_GROUNDED_FEATURE" : groundingLevel + "_MATCH"));
            result.put("groundingLevel", groundingLevel);
            result.put("unsupported", unsupported);
            result.put("fallbackPolicy", FALLBACK);
            return result;
        } catch (Exception malformedGenome) {
            return unsupported(result, genome, "GENOME_IR_UNREADABLE");
        }
    }

    private List<JsonNode> selectMemories(JsonNode ir, String category, String visitorMessage) {
        if (category == null || !ir.path(category).isArray()) return List.of();
        String normalized = visitorMessage == null ? "" : visitorMessage.toLowerCase(Locale.ROOT);
        List<JsonNode> selected = new ArrayList<>();
        for (JsonNode feature : ir.path(category)) {
            if (hasMeaningfulOverlap(normalized, feature.path("statement").asText(""))) selected.add(feature);
        }
        return selected.size() > 3 ? new ArrayList<>(selected.subList(0, 3)) : selected;
    }

    private List<JsonNode> selectPersonaClaims(JsonNode personaLayer, String message) {
        if (!personaLayer.isArray()) return List.of();
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        List<JsonNode> selected = new ArrayList<>();
        JsonNode expressionStyle = null;
        for (JsonNode claim : personaLayer) {
            String type = claim.path("claimType").asText("");
            String value = claim.path("capsuleSafeValue").asText("");
            if ("EXPRESSION_STYLE".equals(type)) {
                expressionStyle = claim;
                continue;
            }
            boolean overlap = hasMeaningfulOverlap(normalized, value);
            boolean cueMatch = switch (type) {
                case "VALUE" -> containsAny(normalized, "看重", "重视", "在意", "价值", "原则",
                        "important", "value");
                case "PREFERENCE" -> containsAny(normalized, "喜欢", "偏好", "更愿意", "prefer", "like");
                case "NEED" -> containsAny(normalized, "需要", "希望", "想要", "need", "hope");
                case "EMOTION_PATTERN" -> containsAny(normalized, "情绪", "感受", "最近", "通常会",
                        "emotion", "feel");
                case "BOUNDARY" -> overlap;
                default -> false;
            };
            if (overlap || cueMatch) selected.add(claim);
        }
        // Style is applicable to the current utterance only. It never grants permission to make
        // a self-description, and it is selected only after no factual/persona claim matched.
        if (selected.isEmpty() && expressionStyle != null) selected.add(expressionStyle);
        return selected.size() > 3 ? new ArrayList<>(selected.subList(0, 3)) : selected;
    }

    private boolean containsAny(String value, String... cues) {
        for (String cue : cues) if (value.contains(cue)) return true;
        return false;
    }

    private boolean isSelfDescriptionClaim(JsonNode claim) {
        return SELF_DESCRIPTION_TYPES.contains(claim.path("claimType").asText(""));
    }

    private boolean isStyleClaim(JsonNode claim) {
        return STYLE_TYPES.contains(claim.path("claimType").asText(""));
    }

    private String classify(String message, JsonNode ir) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        for (String intent : List.of("TEMPORAL", "HABIT", "VALUE", "CLAIM")) {
            if (INTENT_CUES.get(intent).stream().anyMatch(normalized::contains)) return intent;
        }
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

    private Set<Long> claimIds(List<JsonNode> claims) {
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode claim : claims) if (claim.hasNonNull("claimId")) ids.add(claim.path("claimId").asLong());
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
            for (String key : List.of("voice", "dominantSentiment", "confidence", "notBeautified",
                    "boundary", "calibration")) {
                if (style.has(key)) bounded.put(key, objectMapper.convertValue(style.get(key), Object.class));
            }
            return bounded;
        } catch (Exception ignored) {
            return Map.of("unreadable", true);
        }
    }

    private Map<String, Object> unsupported(Map<String, Object> result, CapsuleGenomeVersion genome,
                                             String reason) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("schemaVersion", "capsule-runtime-context.v1");
        context.put("selectedFeatures", List.of());
        context.put("selectedPersonaClaims", List.of());
        context.put("unknowns", List.of(reason));
        result.put("queryIntent", "UNFAMILIAR");
        result.put("selectedCategories", List.of());
        result.put("selectedEvidenceSummary", "");
        result.put("selectedContext", context);
        result.put("contextBuildManifest", manifest(genome, "UNFAMILIAR", List.of(), Set.of(), Set.of(),
                "UNSUPPORTED", true, reason));
        result.put("groundingLevel", "UNSUPPORTED");
        result.put("unsupported", true);
        result.put("fallbackPolicy", FALLBACK);
        return result;
    }

    private Map<String, Object> manifest(CapsuleGenomeVersion genome, String intent,
                                          List<String> categories, Set<Long> memoryIds,
                                          Set<Long> claimIds, String groundingLevel,
                                          boolean unsupported, String reason) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", "context-build-manifest.v2");
        manifest.put("genomeVersionId", genome == null ? null : genome.id);
        manifest.put("genomeVersionNo", genome == null ? null : genome.versionNo);
        manifest.put("compilerVersion", genome == null ? null : genome.compilerVersion);
        manifest.put("queryIntent", intent);
        manifest.put("selectedCategories", categories);
        manifest.put("selectedMemoryIds", new ArrayList<>(memoryIds));
        manifest.put("selectedClaimIds", new ArrayList<>(claimIds));
        manifest.put("groundingLevel", groundingLevel);
        manifest.put("unsupported", unsupported);
        manifest.put("selectionReason", reason);
        manifest.put("fallbackPolicy", FALLBACK);
        return manifest;
    }
}
