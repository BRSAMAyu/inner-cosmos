package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.config.ExperienceModeProperties;
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
    private final ExperienceModeProperties experience;

    @org.springframework.beans.factory.annotation.Autowired
    public CapsuleRuntimeContextComposer(ObjectMapper objectMapper,
                                         ExperienceModeProperties experience) {
        this.objectMapper = objectMapper;
        this.experience = experience;
    }

    /** Compatibility constructor for focused evaluations that instantiate the composer directly. */
    public CapsuleRuntimeContextComposer(ObjectMapper objectMapper) {
        this(objectMapper, new ExperienceModeProperties());
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
            // Experience-first (item 9 "拟真度非常低 / 老是说 unsupported"): a lexical-overlap miss is
            // not the same thing as "this capsule has nothing it is allowed to say". When nothing
            // matched literally but the owner DID authorize self-description/voice claims, fall back
            // to that authorized voice instead of collapsing to UNSUPPORTED and answering every
            // ordinary opener with a disclaimer. This never widens authorization: the candidates are
            // exactly the compiled personaLayer entries the owner already selected.
            boolean expressiveFallback = false;
            boolean hasSelectedSelfDescription =
                    personaClaims.stream().anyMatch(this::isSelfDescriptionClaim);
            if (!hasSelectedSelfDescription && selectedMemories.isEmpty()
                    && experience.expressiveGrounding()) {
                personaClaims = authorizedVoiceFallback(preview.path("personaLayer"));
                expressiveFallback = !personaClaims.isEmpty();
            }
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
                            ? "NO_RELEVANT_GROUNDED_FEATURE"
                            : expressiveFallback
                            ? "AUTHORIZED_VOICE_FALLBACK"
                            : groundingLevel + "_MATCH"));
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
        // A category-wide question such as “你最近怎么样” or “你最重视什么” can be
        // semantically precise while sharing only the category cue with the stored statement.
        // In that case returning the bounded, already-authorized slice of that exact category is
        // safer and more accurate than weakening the global overlap gate (which protects against
        // unrelated generic bigram matches). CLAIM remains exact-overlap only.
        if (selected.isEmpty() && (explicitCategoryQuestion(category, normalized)
                || ("claims".equals(category) && hasSpecificClaimOverlap(ir.path(category), normalized)))) {
            ir.path(category).forEach(selected::add);
        }
        return selected.size() > 3 ? new ArrayList<>(selected.subList(0, 3)) : selected;
    }

    private boolean explicitCategoryQuestion(String category, String normalized) {
        String intent = switch (category) {
            case "temporalState" -> "TEMPORAL";
            case "habits" -> "HABIT";
            case "values" -> "VALUE";
            default -> "";
        };
        return !intent.isBlank() && INTENT_CUES.get(intent).stream().anyMatch(normalized::contains);
    }

    /**
     * A claim question may name one compact entity (for example a two-character city) while the
     * stored episode phrases the action differently. Keep the general two-token privacy gate, but
     * allow the already-authorized claim slice when a non-generic exact entity bigram is shared.
     */
    private boolean hasSpecificClaimOverlap(JsonNode claims, String normalized) {
        Set<String> queryTokens = tokens(normalized);
        Set<String> generic = Set.of("经历", "发生", "记得", "做过", "去过", "住在",
                "experience", "happened");
        for (JsonNode claim : claims) {
            Set<String> overlap = tokens(claim.path("statement").asText("").toLowerCase(Locale.ROOT));
            overlap.retainAll(queryTokens);
            if (overlap.stream().anyMatch(token -> token.length() >= 2 && !generic.contains(token))) {
                return true;
            }
        }
        return false;
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

    /**
     * The capsule's own authorized voice, used when this turn matched nothing lexically.
     * Prefers EXPRESSION_STYLE (how the owner sounds — safe for any utterance) plus at most two
     * highest-confidence self-description claims, so the capsule can answer "hi, how are you?" as
     * itself rather than as a refusal. Anything not in the compiled personaLayer is unreachable
     * here, exactly as before.
     */
    private List<JsonNode> authorizedVoiceFallback(JsonNode personaLayer) {
        if (!personaLayer.isArray()) return List.of();
        List<JsonNode> style = new ArrayList<>();
        List<JsonNode> selfDescription = new ArrayList<>();
        for (JsonNode claim : personaLayer) {
            String type = claim.path("claimType").asText("");
            if (STYLE_TYPES.contains(type)) style.add(claim);
            else if (SELF_DESCRIPTION_TYPES.contains(type)) selfDescription.add(claim);
        }
        selfDescription.sort((left, right) -> Double.compare(
                right.path("confidence").asDouble(0), left.path("confidence").asDouble(0)));
        List<JsonNode> selected = new ArrayList<>(style);
        for (JsonNode claim : selfDescription) {
            if (selected.size() >= 3) break;
            selected.add(claim);
        }
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

    // FIX 5 (privacy-minimisation / relevance tightening): the number of DISTINCT overlapping
    // tokens required before a single shared 2-character bigram is trusted as evidence relevance.
    // A lone shared bigram (e.g. one common Chinese word like "最近"/"自己"/"工作") is common
    // enough between totally unrelated sentences that it must NOT be sufficient by itself -- see
    // hasMeaningfulOverlap for the full rule.
    private static final int MIN_OVERLAP_COUNT = 2;

    /**
     * FIX 5b (informativeness, replacing a pure count threshold): 2-character tokens that carry
     * almost no topical signal on their own. Two unrelated Chinese sentences share one of these
     * routinely, so a lone match on one of them must not select an owner's memory as evidence.
     *
     * <p>Deliberately contains every 2-character member of {@link #INTENT_CUES}: those words
     * already did their job in {@link #classify}, routing the turn to a category. Re-counting the
     * same word as independent *evidence* inside that category is double-dipping on one signal.
     *
     * <p>What is NOT here matters just as much: a specific 2-character token such as a place name
     * ("成都") or a proper noun is genuinely informative, and a lone match on it is real topical
     * relevance -- so it stays sufficient. This is the difference between counting overlaps and
     * weighing them, and it is why the rule below is not simply "require two".
     */
    private static final Set<String> LOW_INFORMATION_TOKENS = Set.of(
            // every 2-character INTENT_CUES entry (TEMPORAL / HABIT / VALUE / CLAIM)
            "最近", "现在", "目前", "近况", "习惯", "通常", "总是", "倾向",
            "重视", "看重", "在意", "原则", "价值", "重要", "经历", "发生",
            "记得", "做过", "去过", "住在",
            // generic high-frequency fillers with no topical content
            "自己", "我们", "他们", "这个", "那个", "什么", "怎么", "可以",
            "一个", "一次", "时候", "事情", "问题", "感觉", "觉得", "没有",
            "有些", "这样", "那样", "开始", "还是", "已经", "因为", "所以");

    /**
     * FIX 5: previously a single shared 2-character bigram was enough to treat `left` and `right`
     * as meaningfully related, which for Chinese meant one common short word (temporal fillers
     * like "最近"/"现在", generic nouns like "自己"/"工作") could pull an owner's private episodic
     * memory in as "evidence" for a loosely-related visitor question -- a relevance/fidelity and
     * privacy-minimisation problem, not an authorization-boundary problem (only compiled,
     * authorized evidence can ever be selected in the first place).
     *
     * <p>Tightened, conservative rule -- true only when at least one of:
     * <ul>
     *   <li>(a) at least {@link #MIN_OVERLAP_COUNT} distinct overlapping tokens, i.e. two
     *       independently-matching short words rather than one coincidental common word; or</li>
     *   <li>(b) any single overlapping token that is itself length &gt;= 3 -- a genuine
     *       length&gt;=3 exact substring (a real Chinese trigram, now also produced by
     *       {@link #tokens}) or an English word of length &gt;= 3. A verbatim 3+-character run
     *       shared between two texts is far less likely to be coincidental than one common
     *       2-character word, so it is treated as sufficient on its own.</li>
     * </ul>
     * This still allows a genuinely on-topic short message (two matching cues, or one specific
     * shared phrase/word) to select evidence -- it only rejects the single-generic-bigram case.
     */
    private boolean hasMeaningfulOverlap(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right.toLowerCase(Locale.ROOT));
        leftTokens.retainAll(rightTokens);
        if (leftTokens.isEmpty()) return false;
        if (leftTokens.size() >= MIN_OVERLAP_COUNT) return true;
        if (leftTokens.stream().anyMatch(token -> token.length() >= 3)) return true;
        // (c) FIX 5b: a SINGLE 2-character token still counts when the token itself is
        // informative. Judging a lone overlap by what the word is beats judging it by how many
        // overlaps there happen to be: "成都" alone is real topical relevance, "最近" alone is not.
        // Without this, asking "你有住在成都的经历吗？" refuses to surface the owner's own
        // explicitly-authorized 成都 episode -- a false negative that makes the capsule useless
        // on precisely the specific questions it should answer best.
        return leftTokens.stream().noneMatch(LOW_INFORMATION_TOKENS::contains);
    }

    private Set<String> tokens(String text) {
        String normalized = text.replaceAll("[\\p{P}\\p{S}\\s]+", "");
        Set<String> tokens = new LinkedHashSet<>();
        for (int i = 0; i + 1 < normalized.length(); i++) tokens.add(normalized.substring(i, i + 2));
        // FIX 5: trigrams let a genuinely contiguous 3-character shared run count as a strong
        // signal on its own (see hasMeaningfulOverlap rule (b)) instead of needing two separate
        // bigram matches to clear MIN_OVERLAP_COUNT.
        for (int i = 0; i + 2 < normalized.length(); i++) tokens.add(normalized.substring(i, i + 3));
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
