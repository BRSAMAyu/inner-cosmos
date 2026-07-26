package com.innercosmos.ai.capsule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.innercosmos.ai.structured.StructuredAiResults;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Closed-vocabulary boundary between an owner's free-form correction and a runnable Capsule
 * Genome. Raw comments are never copied into a prompt or Genome artifact. Even if a provider
 * returns injected/arbitrary values, only these codes survive.
 */
public final class CapsuleCalibrationPolicy {
    public static final String SCHEMA_VERSION = "capsule-calibration-signals.v1";

    public static final Set<String> TONE_CODES = Set.of(
            "DIRECT", "CONCISE", "WARM", "RESTRAINED", "PLAYFUL",
            "DRY_HUMOR", "ANALYTICAL", "CASUAL", "POETIC");
    public static final Set<String> AVOID_BEHAVIOR_CODES = Set.of(
            "GENERIC_CHATBOT_VOICE", "OVER_REASSURANCE", "UNSOLICITED_ADVICE",
            "OVER_QUESTIONING", "OVERLY_FORMAL", "OVERLY_INTIMATE",
            "LONG_RESPONSES", "MORALISING", "SPEAKING_FOR_OWNER");
    public static final Set<String> BOUNDARY_CODES = Set.of(
            "GROUND_FACTS_ONLY", "MINIMIZE_PERSONAL_DETAIL", "NO_CONTACT_DETAILS",
            "PRESERVE_UNCERTAINTY");
    public static final Set<String> RESPONSE_LENGTH_CODES = Set.of("SHORT", "BALANCED", "EXPANSIVE");

    private CapsuleCalibrationPolicy() {
    }

    public static ObjectNode canonical(ObjectMapper objectMapper,
                                       StructuredAiResults.CapsuleCalibrationResult candidate,
                                       String rating) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("schemaVersion", SCHEMA_VERSION);
        writeCodes(result.putArray("toneCodes"), candidate == null ? null : candidate.toneCodes, TONE_CODES);
        LinkedHashSet<String> avoids = allowed(candidate == null ? null : candidate.avoidBehaviorCodes,
                AVOID_BEHAVIOR_CODES);
        LinkedHashSet<String> boundaries = allowed(candidate == null ? null : candidate.boundaryCodes,
                BOUNDARY_CODES);
        applyRatingDefaults(rating, avoids, boundaries);
        writeCodes(result.putArray("avoidBehaviorCodes"), avoids, AVOID_BEHAVIOR_CODES);
        writeCodes(result.putArray("boundaryCodes"), boundaries, BOUNDARY_CODES);
        String responseLength = normalized(candidate == null ? null : candidate.responseLengthCode);
        if (RESPONSE_LENGTH_CODES.contains(responseLength)) {
            result.put("responseLengthCode", responseLength);
        }
        result.put("reinforceCurrentStyle", "LIKE_ME".equals(normalized(rating)));
        return result;
    }

    public static StructuredAiResults.CapsuleCalibrationResult deterministic(String comment, String rating) {
        String text = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
        StructuredAiResults.CapsuleCalibrationResult result =
                new StructuredAiResults.CapsuleCalibrationResult();
        addWhen(result.toneCodes, text, "DIRECT", "直接", "直白", "坦率", "straightforward", "direct");
        addWhen(result.toneCodes, text, "CONCISE", "简短", "简洁", "少说", "concise", "brief");
        addWhen(result.toneCodes, text, "WARM", "温柔", "温暖", "warm");
        addWhen(result.toneCodes, text, "RESTRAINED", "克制", "冷静", "平静", "restrained", "calm");
        addWhen(result.toneCodes, text, "PLAYFUL", "活泼", "俏皮", "好玩", "playful");
        addWhen(result.toneCodes, text, "DRY_HUMOR", "冷幽默", "黑色幽默", "dry humor", "deadpan");
        addWhen(result.toneCodes, text, "ANALYTICAL", "理性", "分析", "逻辑", "analytical", "logical");
        addWhen(result.toneCodes, text, "CASUAL", "随意", "口语", "casual");
        addWhen(result.toneCodes, text, "POETIC", "诗意", "有画面", "poetic");

        addWhen(result.avoidBehaviorCodes, text, "OVER_REASSURANCE",
                "别安慰", "不要安慰", "过度安慰", "reassur");
        addWhen(result.avoidBehaviorCodes, text, "UNSOLICITED_ADVICE",
                "别建议", "不要建议", "说教", "advice", "lecture");
        addWhen(result.avoidBehaviorCodes, text, "OVER_QUESTIONING",
                "别总问", "问题太多", "反问", "too many questions", "interrogate");
        addWhen(result.avoidBehaviorCodes, text, "OVERLY_FORMAL",
                "太正式", "官腔", "formal");
        addWhen(result.avoidBehaviorCodes, text, "OVERLY_INTIMATE",
                "太亲密", "黏腻", "宝贝", "intimate", "clingy");
        addWhen(result.avoidBehaviorCodes, text, "LONG_RESPONSES",
                "太长", "啰嗦", "冗长", "too long", "verbose");
        addWhen(result.avoidBehaviorCodes, text, "MORALISING",
                "鸡汤", "大道理", "moral");
        addWhen(result.avoidBehaviorCodes, text, "SPEAKING_FOR_OWNER",
                "别替我", "不要替我", "代表我", "speak for me");
        if (text.contains("短一点") || text.contains("简短") || text.contains("shorter")) {
            result.responseLengthCode = "SHORT";
        } else if (text.contains("展开") || text.contains("详细") || text.contains("more detail")) {
            result.responseLengthCode = "EXPANSIVE";
        }

        if ("TONE_WRONG".equals(normalized(rating)) && result.toneCodes.isEmpty()
                && result.avoidBehaviorCodes.isEmpty()) {
            result.avoidBehaviorCodes.add("GENERIC_CHATBOT_VOICE");
        }
        if ("NOT_ME".equals(normalized(rating))) {
            result.avoidBehaviorCodes.add("GENERIC_CHATBOT_VOICE");
        }
        return result;
    }

    /**
     * Merge pending, already-canonical signal JSON into a bounded style artifact. Latest explicit
     * tone/length wins; avoidance and privacy constraints only accumulate.
     */
    public static String mergeIntoStyle(ObjectMapper objectMapper,
                                        String styleProfileJson,
                                        List<String> signalJson,
                                        List<Long> feedbackIds,
                                        int targetGenomeVersion) {
        ObjectNode style = parseObject(objectMapper, styleProfileJson);
        ObjectNode calibration = style.path("calibration").isObject()
                ? (ObjectNode) style.path("calibration").deepCopy()
                : objectMapper.createObjectNode();

        LinkedHashSet<String> tone = readAllowed(calibration.path("toneCodes"), TONE_CODES);
        LinkedHashSet<String> avoid = readAllowed(calibration.path("avoidBehaviorCodes"), AVOID_BEHAVIOR_CODES);
        LinkedHashSet<String> boundaries = readAllowed(calibration.path("boundaryCodes"), BOUNDARY_CODES);
        String responseLength = allowedLength(calibration.path("responseLengthCode").asText(null));
        boolean reinforce = calibration.path("reinforceCurrentStyle").asBoolean(false);
        LinkedHashSet<Long> sourceFeedbackIds = new LinkedHashSet<>();
        JsonNode existingSourceIds = calibration.path("sourceFeedbackIds");
        if (existingSourceIds.isArray()) {
            existingSourceIds.forEach(value -> {
                if (value.canConvertToLong()) sourceFeedbackIds.add(value.longValue());
            });
        }

        for (String json : signalJson) {
            ObjectNode signal = parseObject(objectMapper, json);
            LinkedHashSet<String> nextTone = readAllowed(signal.path("toneCodes"), TONE_CODES);
            if (!nextTone.isEmpty()) {
                tone.clear();
                tone.addAll(nextTone);
            }
            avoid.addAll(readAllowed(signal.path("avoidBehaviorCodes"), AVOID_BEHAVIOR_CODES));
            boundaries.addAll(readAllowed(signal.path("boundaryCodes"), BOUNDARY_CODES));
            String nextLength = allowedLength(signal.path("responseLengthCode").asText(null));
            if (nextLength != null) responseLength = nextLength;
            reinforce = signal.path("reinforceCurrentStyle").asBoolean(reinforce);
        }

        calibration.put("schemaVersion", SCHEMA_VERSION);
        writeCodes(calibration.putArray("toneCodes"), tone, TONE_CODES);
        writeCodes(calibration.putArray("avoidBehaviorCodes"), avoid, AVOID_BEHAVIOR_CODES);
        writeCodes(calibration.putArray("boundaryCodes"), boundaries, BOUNDARY_CODES);
        if (responseLength == null) calibration.remove("responseLengthCode");
        else calibration.put("responseLengthCode", responseLength);
        calibration.put("reinforceCurrentStyle", reinforce);
        calibration.put("appliedAtGenomeVersion", targetGenomeVersion);
        ArrayNode sourceIds = calibration.putArray("sourceFeedbackIds");
        feedbackIds.stream().filter(java.util.Objects::nonNull).forEach(sourceFeedbackIds::add);
        sourceFeedbackIds.forEach(sourceIds::add);
        style.set("calibration", calibration);
        try {
            return objectMapper.writeValueAsString(style);
        } catch (Exception impossible) {
            throw new IllegalStateException("Unable to serialize capsule calibration", impossible);
        }
    }

    /**
     * Recompilation regenerates the inferred style profile from the currently authorized memories.
     * Carry only the already-bounded calibration subtree from the immutable parent Genome into that
     * fresh profile; no raw feedback or other stale style fields cross the version boundary.
     */
    public static String carryForwardCalibration(ObjectMapper objectMapper,
                                                 String freshStyleProfileJson,
                                                 String parentStyleProfileJson) {
        ObjectNode fresh = parseObject(objectMapper, freshStyleProfileJson);
        ObjectNode parent = parseObject(objectMapper, parentStyleProfileJson);
        JsonNode calibration = parent.path("calibration");
        if (calibration.isObject()) {
            fresh.set("calibration", calibration.deepCopy());
        }
        try {
            return objectMapper.writeValueAsString(fresh);
        } catch (Exception impossible) {
            throw new IllegalStateException("Unable to carry capsule calibration forward", impossible);
        }
    }

    private static void applyRatingDefaults(String rating, Set<String> avoids, Set<String> boundaries) {
        switch (normalized(rating)) {
            case "NOT_ME" -> avoids.add("GENERIC_CHATBOT_VOICE");
            case "FACT_WRONG" -> boundaries.add("GROUND_FACTS_ONLY");
            case "TOO_EXPOSED" -> {
                boundaries.add("MINIMIZE_PERSONAL_DETAIL");
                boundaries.add("NO_CONTACT_DETAILS");
            }
            default -> {
                // LIKE_ME and TONE_WRONG carry their explicit/deterministic signal only.
            }
        }
    }

    private static void addWhen(List<String> target, String text, String code, String... cues) {
        for (String cue : cues) {
            if (text.contains(cue.toLowerCase(Locale.ROOT))) {
                target.add(code);
                return;
            }
        }
    }

    private static LinkedHashSet<String> allowed(List<String> values, Set<String> allowList) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String code = normalized(value);
                if (allowList.contains(code)) result.add(code);
            }
        }
        return result;
    }

    private static LinkedHashSet<String> readAllowed(JsonNode values, Set<String> allowList) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null && values.isArray()) {
            values.forEach(value -> {
                String code = normalized(value.asText());
                if (allowList.contains(code)) result.add(code);
            });
        }
        return result;
    }

    private static void writeCodes(ArrayNode target, Iterable<String> values, Set<String> allowList) {
        if (values == null) return;
        for (String value : values) {
            String code = normalized(value);
            if (allowList.contains(code)) target.add(code);
        }
    }

    private static ObjectNode parseObject(ObjectMapper objectMapper, String json) {
        try {
            if (json != null && !json.isBlank()) {
                JsonNode parsed = objectMapper.readTree(json);
                if (parsed.isObject()) return (ObjectNode) parsed.deepCopy();
            }
        } catch (Exception ignored) {
            // A malformed old/foreign artifact is replaced with an empty bounded object.
        }
        return objectMapper.createObjectNode();
    }

    private static String allowedLength(String value) {
        String code = normalized(value);
        return RESPONSE_LENGTH_CODES.contains(code) ? code : null;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
