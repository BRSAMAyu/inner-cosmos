package com.innercosmos.vo;

import java.util.List;
import java.util.Map;

/**
 * CP-31 / closing-checklist §2-9: explicit AI-generated content labeling for every surface
 * where capsule-derived content flows out of the platform — owner detail/list, context
 * preview, plaza list/matches, visitor persona-chat replies and data-export records.
 *
 * <p>Provenance tiers, verified against the actual write paths. A field is claimed as
 * AI-generated only when an LLM genuinely produces it — never rounded up:</p>
 *
 * <p><b>1. personaPrompt — AI (LLM) generated.</b> USER_CAPSULE rows are written exclusively
 * by the external-LLM persona compile path ({@code CapsuleAgent#generateUserPersona}, purpose
 * {@code CAPSULE_PERSONA_SYNTHESIS} — provider failure ABORTS capsule creation instead of
 * leaving a template stand-in — and {@code CapsuleContextRegenerator}, purpose
 * {@code CAPSULE_CONTEXT_REGENERATE}). SEED_CAPSULE rows instead carry a platform
 * hand-authored template ({@code MockDataInitializer#seedPersonaPrompt}) and make no AI
 * claim. Known imprecision, disclosed rather than hidden: the non-prod curated demo mirrors
 * (demo/river/cloud showcase USER_CAPSULEs, seeded only when
 * {@code inner-cosmos.demo.seed-enabled=true}) are hand-written fixtures that fall under the
 * USER_CAPSULE rule; per-row provenance is not persisted, and fixing that needs a schema
 * column outside this batch's file domain.</p>
 *
 * <p><b>2. contextPreviewJson / styleProfileJson — system-compiled, NOT LLM.</b> Compiled
 * deterministically from the owner's authorized memories
 * ({@code CapsuleServiceImpl#buildContextPreview} / {@code #inferStyleProfile}: rule-based
 * scene/voice extraction, no provider call); the owner may also overwrite both via
 * create/update requests. They are labeled {@code systemCompiledFields}, never
 * {@code aiGenerated}.</p>
 *
 * <p><b>3. pseudonym / intro / publicTags / ownerContextNote — owner-written</b> (or static
 * platform defaults); never claimed as AI.</p>
 *
 * <p>{@code aiGenerated} carries the same name and meaning as
 * {@link AuroraReplyVO#aiGenerated}: "this payload actually contains LLM-written text".</p>
 */
public final class CapsuleAiLabeling {

    public static final String SEED_CAPSULE_TYPE = "SEED_CAPSULE";

    /** Capsule fields written by the external-LLM persona compile path (see class doc, tier 1). */
    public static final List<String> AI_GENERATED_FIELDS = List.of("personaPrompt");
    /** Capsule fields deterministically compiled from authorized memories (tier 2) — not LLM. */
    public static final List<String> SYSTEM_COMPILED_FIELDS = List.of("contextPreviewJson", "styleProfileJson");
    /** Owner-written (or static platform default) capsule fields (tier 3). */
    public static final List<String> OWNER_WRITTEN_FIELDS = List.of("pseudonym", "intro", "publicTags", "ownerContextNote");

    public static final String LABELING_NOTE =
            "personaPrompt 由外部大模型编译（CapsuleAgent/CAPSULE_PERSONA_SYNTHESIS；SEED_CAPSULE 为平台手写模板，不作 AI 声明）；"
            + "contextPreviewJson/styleProfileJson 由规则编译器从已授权记忆生成（非 LLM，owner 可经创建/更新请求覆写，行级出处未持久化）；"
            + "pseudonym/intro/publicTags/ownerContextNote 为 owner 手写或平台默认文案，不作 AI 声明。";

    private CapsuleAiLabeling() {
    }

    /** SEED capsules carry a platform-authored template persona, not an LLM-compiled one. */
    public static boolean personaPromptIsAiGenerated(String capsuleType) {
        return !SEED_CAPSULE_TYPE.equals(capsuleType);
    }

    /** AI-generated field names applicable to a capsule of the given type. */
    public static List<String> aiGeneratedFieldsFor(String capsuleType) {
        return personaPromptIsAiGenerated(capsuleType) ? AI_GENERATED_FIELDS : List.of();
    }

    /**
     * True only when this payload actually carries a non-blank LLM-written value — a payload
     * whose personaPrompt is still blank (legacy/pre-AI rows) is not labeled AI.
     */
    public static boolean payloadCarriesAiContent(Map<String, Object> payload, String capsuleType) {
        if (!personaPromptIsAiGenerated(capsuleType)) {
            return false;
        }
        Object personaPrompt = payload == null ? null : payload.get("personaPrompt");
        return personaPrompt != null && !String.valueOf(personaPrompt).isBlank();
    }

    /**
     * Augment an already-serialized whole-capsule payload (entity converted to a mutable map)
     * with the labeling keys. Additive only — every pre-existing field stays at its level.
     */
    public static Map<String, Object> augmentCapsulePayload(Map<String, Object> payload, String capsuleType) {
        payload.put("aiGenerated", payloadCarriesAiContent(payload, capsuleType));
        payload.put("aiGeneratedFields", aiGeneratedFieldsFor(capsuleType));
        payload.put("systemCompiledFields", SYSTEM_COMPILED_FIELDS);
        payload.put("ownerWrittenFields", OWNER_WRITTEN_FIELDS);
        payload.put("aiLabelingNote", LABELING_NOTE);
        return payload;
    }

    /**
     * Payload-scoped labeling for the context-preview view, whose payload keys differ from the
     * entity's (contextPreview / styleProfile / ownerContextNote). No LLM-written field is part
     * of this view, so {@code aiGenerated} is false and {@code aiGeneratedFields} empty; the
     * two compiled fields are named honestly as system-compiled. {@code authorizedMemories}
     * shows the owner their own P1 memory cards, whose title/summary provenance is genuinely
     * mixed (AI extraction from dialogs, later owner corrections, imports) — claimed as neither
     * AI nor owner text, and called out in the note instead.
     */
    public static Map<String, Object> augmentContextPreviewPayload(Map<String, Object> payload) {
        payload.put("aiGenerated", false);
        payload.put("aiGeneratedFields", List.of());
        payload.put("systemCompiledFields", List.of("contextPreview", "styleProfile"));
        payload.put("ownerWrittenFields", List.of("pseudonym", "ownerContextNote", "publicTags"));
        payload.put("aiLabelingNote",
                "本视图不含 LLM 生成字段；contextPreview/styleProfile 由规则编译器从已授权记忆生成（非 LLM，owner 可覆写）；"
                + "authorizedMemories 为 owner 自己的 P1 记忆卡，其标题/摘要出处混合（对话 AI 抽取、owner 纠正、导入），"
                + "故不做单一 AI/手写标注。" + LABELING_NOTE);
        return payload;
    }
}
