package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.vo.CapsulePreviewVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

@Service
public class CapsuleServiceImpl implements CapsuleService {
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleBoundaryMapper boundaryMapper;
    private final CapsuleAgent capsuleAgent;
    private final MemoryCardMapper memoryCardMapper;

    public CapsuleServiceImpl(EchoCapsuleMapper capsuleMapper, 
                              CapsuleBoundaryMapper boundaryMapper, 
                              CapsuleAgent capsuleAgent,
                              MemoryCardMapper memoryCardMapper) {
        this.capsuleMapper = capsuleMapper;
        this.boundaryMapper = boundaryMapper;
        this.capsuleAgent = capsuleAgent;
        this.memoryCardMapper = memoryCardMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EchoCapsule createFromMemory(Long userId, CapsuleCreateRequest request) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = userId;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = request.pseudonym == null || request.pseudonym.isBlank() ? "未命名回声" : request.pseudonym;
        capsule.intro = request.intro == null ? "一枚从脱敏记忆中编织出的数字回声." : request.intro;

        // Fetch selected memory cards to synthesize user persona
        List<String> memorySummaries = new java.util.ArrayList<>();
        if (request.memoryIds != null && !request.memoryIds.isEmpty()) {
            for (Long mid : request.memoryIds) {
                MemoryCard card = memoryCardMapper.selectById(mid);
                if (card != null && userId.equals(card.userId)) {
                    memorySummaries.add(card.title + ": " + card.summary);
                }
            }
        }
        if (memorySummaries.isEmpty()) {
            QueryWrapper<MemoryCard> q = new QueryWrapper<>();
            q.eq("user_id", userId).eq("status", "ACTIVE").orderByDesc("emotional_gravity").last("LIMIT 5");
            List<MemoryCard> cards = memoryCardMapper.selectList(q);
            for (MemoryCard card : cards) {
                memorySummaries.add(card.title + ": " + card.summary);
            }
        }
        capsule.personaPrompt = capsuleAgent.generateUserPersona(userId, memorySummaries, capsule.pseudonym, capsule.intro);

        capsule.publicTags = toJsonArray(request.publicTags, "self-resonance");
        capsule.authorizedMemoryIds = toJsonArray(request.memoryIds != null ? request.memoryIds.stream().map(String::valueOf).toList() : null);
        capsule.ownerContextNote = request.ownerContextNote;
        capsule.styleProfileJson = request.styleProfileJson == null ? inferStyleProfile(memorySummaries) : request.styleProfileJson;
        capsule.contextPreviewJson = request.contextPreviewJson == null ? buildContextPreview(memorySummaries, capsule.publicTags, capsule.ownerContextNote) : request.contextPreviewJson;
        capsule.standInEnabled = request.standInEnabled == null ? false : request.standInEnabled;
        capsule.realContactPolicy = request.realContactPolicy == null ? "LETTER_ONLY" : request.realContactPolicy;
        capsule.echoEnergy = 0.72;
        capsule.freshnessScore = 0.86;
        capsule.conversationLimitPerDay = safeTurns(request.maxConversationTurns);
        capsule.visibilityStatus = safeVisibility(request.visibilityStatus);
        capsule.isPublic = request.isPublic == null ? !"PRIVATE".equals(capsule.visibilityStatus) : request.isPublic;
        capsule.lastMemoryUpdateAt = LocalDateTime.now();
        capsuleMapper.insert(capsule);

        CapsuleBoundary boundary = new CapsuleBoundary();
        boundary.capsuleId = capsule.id;
        boundary.allowTopics = toJsonArray(request.allowTopics, "自我观察", "温柔建议", "日常支持");
        boundary.blockedTopics = toJsonArray(request.blockedTopics, "隐私身份", "诊断承诺", "强迫即时回应");
        boundary.maxConversationTurns = safeTurns(request.maxConversationTurns);
        boundary.allowLetterRequest = request.allowLetterRequest == null ? true : request.allowLetterRequest;
        boundary.privacyLevel = safePrivacy(request.privacyLevel);
        boundaryMapper.insert(boundary);
        return capsule;
    }

    @Override
    public EchoCapsule updateContext(Long userId, Long capsuleId, Map<String, Object> body) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        if (body.containsKey("ownerContextNote")) capsule.ownerContextNote = stringValue(body.get("ownerContextNote"));
        if (body.containsKey("styleProfileJson")) capsule.styleProfileJson = stringValue(body.get("styleProfileJson"));
        if (body.containsKey("contextPreviewJson")) capsule.contextPreviewJson = stringValue(body.get("contextPreviewJson"));
        if (body.containsKey("standInEnabled")) capsule.standInEnabled = Boolean.TRUE.equals(body.get("standInEnabled"));
        if (body.containsKey("realContactPolicy")) capsule.realContactPolicy = safeContactPolicy(stringValue(body.get("realContactPolicy")));
        if (body.containsKey("publicTags")) capsule.publicTags = toJsonArray(castStringList(body.get("publicTags")), "self-resonance");
        if (body.containsKey("authorizedMemoryIds")) capsule.authorizedMemoryIds = toJsonArray(castStringList(body.get("authorizedMemoryIds")));
        capsule.lastMemoryUpdateAt = LocalDateTime.now();
        if (capsule.contextPreviewJson == null || capsule.contextPreviewJson.isBlank()) {
            capsule.contextPreviewJson = buildContextPreview(List.of(capsule.intro), capsule.publicTags, capsule.ownerContextNote);
        }
        capsuleMapper.updateById(capsule);
        return capsule;
    }

    @Override
    public Map<String, Object> contextPreview(Long userId, Long capsuleId) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        List<Long> ids = parseLongIds(capsule.authorizedMemoryIds);
        List<Map<String, Object>> memories = new ArrayList<>();
        for (Long id : ids) {
            MemoryCard card = memoryCardMapper.selectById(id);
            if (card != null && userId.equals(card.userId)) {
                memories.add(Map.of(
                        "id", card.id,
                        "title", card.title == null ? "" : card.title,
                        "summary", card.summary == null ? "" : card.summary,
                        "tags", parseTags(card.keywordTags)
                ));
            }
        }
        return Map.of(
                "capsuleId", capsule.id,
                "pseudonym", capsule.pseudonym == null ? "" : capsule.pseudonym,
                "contextPreview", capsule.contextPreviewJson == null ? "" : capsule.contextPreviewJson,
                "styleProfile", capsule.styleProfileJson == null ? "" : capsule.styleProfileJson,
                "ownerContextNote", capsule.ownerContextNote == null ? "" : capsule.ownerContextNote,
                "authorizedMemories", memories,
                "publicTags", parseTags(capsule.publicTags),
                "standInEnabled", Boolean.TRUE.equals(capsule.standInEnabled),
                "realContactPolicy", capsule.realContactPolicy == null ? "LETTER_ONLY" : capsule.realContactPolicy
        );
    }

    @Override
    public EchoCapsule getOwnedCapsule(Long userId, Long capsuleId) {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("id", capsuleId).eq("owner_user_id", userId).last("LIMIT 1");
        return capsuleMapper.selectOne(query);
    }

    @Override
    public EchoCapsule updateVisibility(Long userId, Long capsuleId, String visibilityStatus, Boolean isPublic) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            return null;
        }
        capsule.visibilityStatus = safeVisibility(visibilityStatus);
        capsule.isPublic = isPublic == null ? !"PRIVATE".equals(capsule.visibilityStatus) : isPublic;
        capsuleMapper.updateById(capsule);
        return capsule;
    }

    @Override
    public List<EchoCapsule> myCapsules(Long userId) {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("owner_user_id", userId).orderByDesc("id");
        return capsuleMapper.selectList(query);
    }

    @Override
    public List<EchoCapsule> plazaCapsules() {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("is_public", true).eq("visibility_status", "PUBLIC").orderByDesc("echo_energy");
        return capsuleMapper.selectList(query);
    }

    @Override
    public List<Map<String, Object>> matchedCapsules(Long userId) {
        List<MemoryCard> memories = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId)
                .eq("status", "ACTIVE")
                .orderByDesc("emotional_gravity")
                .last("LIMIT 24"));
        Set<String> userSignals = new LinkedHashSet<>();
        for (MemoryCard memory : memories) {
            userSignals.addAll(parseTags(memory.keywordTags));
            userSignals.addAll(parseTags(memory.emotionTags));
            addTerms(userSignals, memory.title);
            addTerms(userSignals, memory.summary);
        }

        List<EchoCapsule> all = plazaCapsules();
        List<Map<String, Object>> scored = new ArrayList<>();
        for (EchoCapsule capsule : all) {
            if (userId.equals(capsule.ownerUserId)) {
                continue;
            }
            Set<String> capsuleSignals = new LinkedHashSet<>(parseTags(capsule.publicTags));
            addTerms(capsuleSignals, capsule.pseudonym);
            addTerms(capsuleSignals, capsule.intro);

            Set<String> overlap = new LinkedHashSet<>();
            for (String signal : userSignals) {
                if (containsSimilar(capsuleSignals, signal)) {
                    overlap.add(signal);
                }
            }
            double semanticScore = Math.min(0.72, overlap.size() * 0.12);
            double energyScore = Math.min(0.18, (capsule.echoEnergy == null ? 0.5 : capsule.echoEnergy) * 0.18);
            double seedBoost = "SEED_CAPSULE".equals(capsule.capsuleType) ? 0.04 : 0.08;
            double score = Math.min(0.99, semanticScore + energyScore + seedBoost);
            if (score < 0.16) {
                score = 0.16 + energyScore;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("capsule", capsule);
            item.put("matchScore", Math.round(score * 100.0) / 100.0);
            item.put("matchReasons", overlap.stream().limit(5).toList());
            item.put("matchSummary", buildMatchSummary(capsule, overlap));
            scored.add(item);
        }
        scored.sort(Comparator.comparingDouble(v -> -((Number) v.get("matchScore")).doubleValue()));
        return scored.stream().limit(12).toList();
    }

    @Override
    public CapsulePreviewVO previewUserMirror(Long userId) {
        List<MemoryCard> cards = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId)
                .eq("status", "ACTIVE")
                .orderByDesc("emotional_gravity")
                .last("LIMIT 8"));
        CapsulePreviewVO vo = new CapsulePreviewVO();
        vo.removedSensitiveItems = new ArrayList<>();
        vo.riskWarnings = new ArrayList<>();
        vo.publicTags = new ArrayList<>();
        if (cards.isEmpty()) {
            vo.abstractSummary = "还没有足够的长期记忆来生成用户共鸣体。建议先完成一次 Aurora 对话或心声日记。";
            vo.suggestedPseudonym = "新的回声分身";
            vo.personaPromptDraft = capsuleAgent.buildPersonaPrompt(vo.suggestedPseudonym, vo.abstractSummary);
            return vo;
        }
        StringBuilder summary = new StringBuilder();
        List<String> memorySummaries = new ArrayList<>();
        for (MemoryCard card : cards) {
            summary.append("「").append(card.title).append("」").append(card.summary).append("\n");
            memorySummaries.add(card.title + ": " + card.summary);
            vo.publicTags.addAll(parseTags(card.keywordTags).stream().limit(3).toList());
            if (card.intensityScore != null && card.intensityScore >= 7.5) {
                vo.riskWarnings.add("包含高情绪重力记忆，建议公开前再次确认边界。");
            }
        }
        vo.publicTags = vo.publicTags.stream().distinct().limit(8).toList();
        vo.abstractSummary = summary.toString().trim();
        vo.suggestedPseudonym = "我的回声分身";
        vo.personaPromptDraft = capsuleAgent.generateUserPersona(userId, memorySummaries, vo.suggestedPseudonym,
                "一个由授权长期记忆生成的用户共鸣体，用于慢社交中的低压力共鸣对话。");
        vo.personaPromptDraft += "\n\n透明提示：这个共鸣体会保留真实困惑、表达习惯和价值偏好，不会把用户美化成完美人设。访问者看到的是授权后的脱敏回声。";
        vo.removedSensitiveItems.add("原始对话全文");
        vo.removedSensitiveItems.add("真实身份与联系方式");
        return vo;
    }

    @Override
    public CapsuleBoundary getBoundary(Long capsuleId) {
        QueryWrapper<CapsuleBoundary> query = new QueryWrapper<>();
        query.eq("capsule_id", capsuleId).last("LIMIT 1");
        return boundaryMapper.selectOne(query);
    }

    @Override
    public void updateBoundary(Long userId, Long capsuleId, CapsuleBoundary boundary) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        CapsuleBoundary existing = getBoundary(capsuleId);
        if (existing == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "边界配置不存在");
        }
        if (boundary.allowTopics != null) existing.allowTopics = boundary.allowTopics;
        if (boundary.blockedTopics != null) existing.blockedTopics = boundary.blockedTopics;
        if (boundary.maxConversationTurns != null) existing.maxConversationTurns = boundary.maxConversationTurns;
        if (boundary.allowLetterRequest != null) existing.allowLetterRequest = boundary.allowLetterRequest;
        if (boundary.privacyLevel != null) existing.privacyLevel = boundary.privacyLevel;
        boundaryMapper.updateById(existing);
    }

    @Override
    public void archiveCapsule(Long userId, Long capsuleId) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        capsule.visibilityStatus = "ARCHIVED";
        capsule.isPublic = false;
        capsuleMapper.updateById(capsule);
    }

    private Integer safeTurns(Integer turns) {
        if (turns == null) return 5;
        return Math.max(2, Math.min(12, turns));
    }

    private String safeVisibility(String value) {
        if ("PRIVATE".equals(value) || "HIDDEN".equals(value) || "ARCHIVED".equals(value)) {
            return value;
        }
        return "PUBLIC";
    }

    private String safePrivacy(String value) {
        if ("STRICT".equals(value) || "OPEN".equals(value)) {
            return value;
        }
        return "BALANCED";
    }

    private String toJsonArray(List<String> values, String... defaults) {
        List<String> source = values == null || values.isEmpty() ? List.of(defaults) : values;
        return source.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce("[", (a, b) -> "[".equals(a) ? a + b : a + "," + b) + "]";
    }

    private List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        List<String> tags = new ArrayList<>();
        for (String item : cleaned.split(",")) {
            String tag = item.trim().replace("\"", "");
            if (!tag.isBlank()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private void addTerms(Set<String> target, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String term : List.of("项目", "考试", "关系", "朋友", "边界", "孤独", "日记", "真实AI", "Aurora", "行动", "拖延", "深夜", "睡前", "产品", "理解", "慢社交")) {
            if (text.contains(term)) {
                target.add(term);
            }
        }
    }

    private boolean containsSimilar(Set<String> haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        for (String item : haystack) {
            if (item.equals(needle) || item.contains(needle) || needle.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private String buildMatchSummary(EchoCapsule capsule, Set<String> overlap) {
        if (overlap.isEmpty()) {
            return "基于回声能量和公开边界推荐。";
        }
        String type = "SEED_CAPSULE".equals(capsule.capsuleType) ? "官方种子" : "用户回声";
        return type + "与你最近的「" + String.join("、", overlap.stream().limit(3).toList()) + "」线索有重合。";
    }

    private String inferStyleProfile(List<String> memorySummaries) {
        String joined = String.join("\n", memorySummaries);
        List<String> style = new ArrayList<>();
        if (joined.contains("真实") || joined.contains("模板")) style.add("对空泛话术敏感");
        if (joined.contains("项目") || joined.contains("行动")) style.add("重视可验证的小行动");
        if (joined.contains("关系") || joined.contains("朋友")) style.add("在关系中重视被认真回应");
        if (style.isEmpty()) style.add("温和、诚实、慢热");
        return "{\"voice\":\"" + String.join("，", style) + "\",\"notBeautified\":true,\"boundary\":\"只呈现授权后的真实片段，不替本人承诺\"}";
    }

    private String buildContextPreview(List<String> memorySummaries, String publicTags, String note) {
        return "{\"visibleSummary\":\"" + escapeJson(String.join(" / ", memorySummaries).replaceAll("\\s+", " ").substring(0, Math.min(420, String.join(" / ", memorySummaries).length()))) +
                "\",\"publicTags\":" + (publicTags == null || publicTags.isBlank() ? "[]" : publicTags) +
                ",\"ownerNote\":\"" + escapeJson(note == null ? "" : note) +
                "\",\"privacy\":\"不包含原始对话全文、联系方式、真实身份和未授权记忆\"}";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeContactPolicy(String value) {
        if ("DIRECT_REQUEST".equals(value) || "STAND_IN_FIRST".equals(value) || "NO_REAL_CONTACT".equals(value)) return value;
        return "LETTER_ONLY";
    }

    private List<String> castStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        if (raw == null || String.valueOf(raw).isBlank()) return List.of();
        return Arrays.stream(String.valueOf(raw).split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<Long> parseLongIds(String raw) {
        List<Long> result = new ArrayList<>();
        for (String item : parseTags(raw)) {
            try {
                result.add(Long.parseLong(item.replace("#", "")));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
