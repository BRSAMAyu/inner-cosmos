package com.innercosmos.ai.capsule;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.EchoCapsuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Regenerates Echo Capsule context (persona_prompt and context_preview_json)
 * from filtered portrait data when user portrait or relationship changes.
 */
@Component
public class CapsuleContextRegenerator {
    private static final Logger log = LoggerFactory.getLogger(CapsuleContextRegenerator.class);

    private final LlmClient llmClient;
    private final EchoCapsuleMapper capsuleMapper;

    public CapsuleContextRegenerator(LlmClient llmClient, EchoCapsuleMapper capsuleMapper) {
        this.llmClient = llmClient;
        this.capsuleMapper = capsuleMapper;
    }

    /**
     * Regenerate persona prompt for a capsule based on filtered portrait data.
     *
     * @param capsuleId The capsule to update
     * @param portrait      Filtered (PII-redacted) portrait data
     * @param recentThemes  Recent memory themes to inform the regeneration
     */
    @Transactional
    public void regenerate(Long capsuleId,
                           PiiPrivacyFilter.FilteredPortrait portrait,
                           List<String> recentThemes) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null) {
            // IC-CAP-002 FIX-1: a missing capsule is a real failure — propagate so the
            // caller records FAILED rather than a phantom SYNCED for a row that never synced.
            throw new IllegalStateException("Capsule " + capsuleId + " not found for context regeneration");
        }

        // IC-CAP-002 FIX-1: do NOT swallow LLM failures. Any exception from llmClient.chat
        // must propagate to CapsuleSyncService.regenerateOne so the queue row is marked
        // FAILED (with retry bookkeeping + SYNC_FAILED notification), instead of falling
        // through to the success branch (which reported SYNCED for every real LLM outage).
        String personaPrompt = buildRegenerationPrompt(capsule, portrait, recentThemes);
        LlmRequest req = new LlmRequest(capsule.ownerUserId, "CAPSULE_CONTEXT_REGENERATE", personaPrompt);
        String newPrompt = llmClient.chat(req);
        if (newPrompt == null || newPrompt.isBlank()) {
            // Empty/blank LLM output is a failure, not a silent no-op.
            throw new IllegalStateException("LLM returned empty response for capsule " + capsuleId);
        }
        capsule.personaPrompt = newPrompt.trim();
        capsule.contextPreviewJson = buildContextPreviewJson(portrait, capsule);
        capsuleMapper.updateById(capsule);
        log.info("Capsule {} context regenerated successfully", capsuleId);
    }

    private String buildRegenerationPrompt(EchoCapsule capsule,
                                           PiiPrivacyFilter.FilteredPortrait portrait,
                                           List<String> recentThemes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是共鸣体\"").append(portrait.pseudonym() != null ? portrait.pseudonym() : capsule.pseudonym)
                .append("\"的人格更新引擎。\n\n");
        prompt.append("现有简介：").append(capsule.intro != null ? capsule.intro : "一枚数字回声").append("\n\n");
        prompt.append("脱敏后的用户画像信息：\n");

        if (portrait.city() != null) {
            prompt.append("- 所在地区：").append(portrait.city()).append("\n");
        }
        if (portrait.ageRange() != null) {
            prompt.append("- 年龄段：").append(portrait.ageRange()).append("\n");
        }
        if (portrait.occupationCategory() != null) {
            prompt.append("- 职业领域：").append(portrait.occupationCategory()).append("\n");
        }
        if (!portrait.values().isEmpty()) {
            prompt.append("- 核心价值：").append(String.join("、", portrait.values())).append("\n");
        }
        if (!portrait.auroraRoles().isEmpty()) {
            prompt.append("- Aurora的角色：").append(String.join("、", portrait.auroraRoles())).append("\n");
        }
        if (portrait.agencyBoundary() != null && !portrait.agencyBoundary().isBlank()) {
            prompt.append("- 边界说明：").append(portrait.agencyBoundary()).append("\n");
        }
        if (recentThemes != null && !recentThemes.isEmpty()) {
            prompt.append("\n最近关注的主题：").append(String.join("、", recentThemes)).append("\n");
        }

        prompt.append("""

            任务：根据以上脱敏画像，为该共鸣体生成一段更新后的"人格系统设定提示词"（System Prompt）。
            要求：
            1. 保持共鸣体的语言风格和性格特质
            2. 融入新的脱敏画像信息（年龄段、价值观等）
            3. 不暴露任何个人身份信息（姓名、具体地址、精确年龄等）
            4. 语气符合该用户的性格特质
            5. 直接返回生成的 System Prompt 纯文本，不包含 JSON 或 Markdown 包裹
            """);

        return prompt.toString();
    }

    private String buildContextPreviewJson(PiiPrivacyFilter.FilteredPortrait portrait, EchoCapsule capsule) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"visibleSummary\":\"");
        if (portrait.occupationCategory() != null) {
            sb.append(escapeJson(portrait.occupationCategory())).append("领域，");
        }
        if (portrait.ageRange() != null) {
            sb.append(escapeJson(portrait.ageRange())).append("岁，");
        }
        if (!portrait.values().isEmpty()) {
            sb.append("关注").append(escapeJson(String.join("、", portrait.values()))).append("，");
        }
        sb.append(escapeJson(capsule.intro != null ? capsule.intro : "一枚数字回声"));
        sb.append("\",\"publicTags\":").append(capsule.publicTags != null ? capsule.publicTags : "[]");
        if (portrait.city() != null) {
            sb.append(",\"city\":\"").append(escapeJson(portrait.city())).append("\"");
        }
        sb.append(",\"privacy\":\"不包含原始对话全文、联系方式、真实身份和未授权记忆\"}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}