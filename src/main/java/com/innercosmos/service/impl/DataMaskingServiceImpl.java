package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.DataMaskingService;
import com.innercosmos.vo.CapsulePreviewVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataMaskingServiceImpl implements DataMaskingService {
    private final MemoryCardMapper memoryCardMapper;
    private final AuthorizedMemoryRefMapper authorizedMemoryRefMapper;

    public DataMaskingServiceImpl(MemoryCardMapper memoryCardMapper,
                                  AuthorizedMemoryRefMapper authorizedMemoryRefMapper) {
        this.memoryCardMapper = memoryCardMapper;
        this.authorizedMemoryRefMapper = authorizedMemoryRefMapper;
    }

    @Override
    public CapsulePreviewVO previewFromMemory(Long userId, List<Long> memoryIds,
                                                String privacyLevel, List<String> allowTopics, List<String> blockedTopics) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            CapsulePreviewVO emptyPreview = new CapsulePreviewVO();
            emptyPreview.removedSensitiveItems = new ArrayList<>();
            emptyPreview.publicTags = new ArrayList<>();
            emptyPreview.riskWarnings = new ArrayList<>();
            emptyPreview.abstractSummary = "暂无摘要";
            emptyPreview.suggestedPseudonym = "星际旅人";
            emptyPreview.personaPromptDraft = "这是一个空的共鸣体预览。";
            return emptyPreview;
        }
        // Load MemoryCards
        QueryWrapper<MemoryCard> query = new QueryWrapper<>();
        query.eq("user_id", userId).in("id", memoryIds).eq("status", "ACTIVE");
        List<MemoryCard> cards = memoryCardMapper.selectList(query);

        CapsulePreviewVO preview = new CapsulePreviewVO();
        preview.removedSensitiveItems = new ArrayList<>();
        preview.publicTags = new ArrayList<>();
        preview.riskWarnings = new ArrayList<>();

        // Generate abstract summary with masking applied
        StringBuilder summaryBuilder = new StringBuilder();
        for (MemoryCard card : cards) {
            String masked = maskText(card.summary, privacyLevel);
            summaryBuilder.append(masked).append("；");
        }
        preview.abstractSummary = summaryBuilder.length() > 0
                ? summaryBuilder.substring(0, summaryBuilder.length() - 1)
                : "暂无摘要";

        // Collect public tags (only safe keywords)
        for (MemoryCard card : cards) {
            if (card.keywordTags != null) {
                String tags = card.keywordTags.replaceAll("[\\[\\]\"]", "");
                for (String tag : tags.split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty() && !isSensitive(trimmed)) {
                        if (!preview.publicTags.contains(trimmed)) {
                            preview.publicTags.add(trimmed);
                        }
                    }
                }
            }
        }

        // Generate suggested pseudonym
        preview.suggestedPseudonym = generatePseudonym(cards);

        // Generate persona prompt draft
        preview.personaPromptDraft = generatePersonaPrompt(preview.abstractSummary, preview.publicTags);

        // Check for risk warnings
        for (MemoryCard card : cards) {
            if (card.intensityScore != null && card.intensityScore > 7.0) {
                preview.riskWarnings.add("包含高情绪强度记忆，建议谨慎公开");
            }
            if (containsSensitiveContent(card.summary)) {
                preview.riskWarnings.add("部分内容可能包含敏感信息，已自动脱敏");
                preview.removedSensitiveItems.add("个人识别信息");
            }
        }

        // Check blocked topics
        if (blockedTopics != null) {
            for (String blocked : blockedTopics) {
                for (MemoryCard card : cards) {
                    if (card.keywordTags != null && card.keywordTags.contains(blocked)) {
                        preview.riskWarnings.add("包含被限制话题：" + blocked);
                        preview.removedSensitiveItems.add(blocked);
                    }
                }
            }
        }

        return preview;
    }

    @Override
    public String maskText(String raw, String privacyLevel) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String result = raw;

        if ("STRICT".equalsIgnoreCase(privacyLevel)) {
            // Strict masking: remove names, places, schools, dates, contact info
            result = maskPatterns(result);
        } else if ("MODERATE".equalsIgnoreCase(privacyLevel)) {
            // Moderate masking: remove names and contact info only
            result = maskContactInfo(result);
            result = maskNames(result);
        }
        // LOW or default: minimal masking
        result = maskContactInfo(result);

        return result;
    }

    private String maskPatterns(String text) {
        // Mask phone numbers
        text = text.replaceAll("1[3-9]\\d{9}", "***********");
        // Mask email addresses
        text = text.replaceAll("[\\w.-]+@[\\w.-]+\\.\\w+", "***@***.***");
        // Mask common school names pattern
        text = text.replaceAll(".{2,6}(大学|学院|中学|小学|学校)", "***学校");
        // Mask QQ numbers
        text = text.replaceAll("QQ\\s*[:：]?\\s*\\d{5,12}", "QQ:*******");
        // Mask WeChat IDs
        text = text.replaceAll("微信\\s*[:：]?\\s*\\S+", "微信:*******");
        return text;
    }

    private String maskContactInfo(String text) {
        text = text.replaceAll("1[3-9]\\d{9}", "***********");
        text = text.replaceAll("[\\w.-]+@[\\w.-]+\\.\\w+", "***@***.***");
        return text;
    }

    private String maskNames(String text) {
        // Simple approach: mask common name patterns after certain keywords
        text = text.replaceAll("叫(.{2,4})(的|了|是|，|。|\\s)", "叫***$2");
        text = text.replaceAll("我是(.{2,4})(的|了|是|，|。|\\s)", "我是***$2");
        return text;
    }

    private boolean isSensitive(String keyword) {
        List<String> sensitiveWords = List.of("姓名", "电话", "地址", "学校", "密码", "账号", "身份证");
        return sensitiveWords.contains(keyword);
    }

    private boolean containsSensitiveContent(String text) {
        if (text == null) return false;
        // Check for phone numbers
        if (text.matches(".*1[3-9]\\d{9}.*")) return true;
        // Check for email
        if (text.matches(".*[\\w.-]+@[\\w.-]+\\.\\w+.*")) return true;
        // Check for school names
        if (text.matches(".*.{2,6}(大学|学院|中学|小学).*")) return true;
        return false;
    }

    private String generatePseudonym(List<MemoryCard> cards) {
        if (cards.isEmpty()) return "星际旅人";
        MemoryCard first = cards.get(0);
        if (first.memoryType == null) return "星际旅人";
        switch (first.memoryType) {
            case "TODO": return "行动派探索者";
            case "RELATION": return "温暖连接者";
            case "COGNITION": return "深度思考者";
            case "EMOTION": return "感性漫步者";
            default: return "星际旅人";
        }
    }

    private String generatePersonaPrompt(String summary, List<String> tags) {
        String tagStr = tags.isEmpty() ? "日常" : String.join("、", tags);
        return "这是一个关于" + tagStr + "的共鸣体。" +
                "它承载了一段真实但已脱敏的经历。" +
                "与它对话时，请保持温柔和尊重，不要试图猜测真实身份。";
    }
}
