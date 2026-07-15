package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.PsychologySkillRunRequest;
import com.innercosmos.entity.PsychologySkillRun;
import com.innercosmos.entity.PsychologySkillRelease;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.PsychologySkillRunMapper;
import com.innercosmos.safety.SafetyBoundaryFilter;
import com.innercosmos.service.PsychologySkillService;
import com.innercosmos.service.PsychologySkillReleaseService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.skill.PsychologySkillManifest;
import com.innercosmos.skill.PsychologySkillRegistry;
import com.innercosmos.vo.PsychologySkillRunVO;
import com.innercosmos.vo.PsychologySkillSuggestionVO;
import com.innercosmos.vo.SafetyResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class PsychologySkillServiceImpl implements PsychologySkillService {
    private static final Set<String> RETENTION = Set.of("DISCARD_AFTER_SESSION", "SAVE_RESULT", "PROFILE_ELIGIBLE");
    private static final Set<String> LOCALES = Set.of("zh-CN", "en-SG");
    private static final int ANSWER_MAX_CHARS = 1200;

    private final PsychologySkillRegistry registry;
    private final PsychologySkillRunMapper mapper;
    private final PsychologySkillReleaseService releaseService;
    private final SafetyService safetyService;
    private final SafetyBoundaryFilter safetyBoundaryFilter;
    private final ObjectMapper objectMapper;

    public PsychologySkillServiceImpl(PsychologySkillRegistry registry, PsychologySkillRunMapper mapper,
                                      PsychologySkillReleaseService releaseService,
                                      SafetyService safetyService, SafetyBoundaryFilter safetyBoundaryFilter,
                                      ObjectMapper objectMapper) {
        this.registry = registry;
        this.mapper = mapper;
        this.releaseService = releaseService;
        this.safetyService = safetyService;
        this.safetyBoundaryFilter = safetyBoundaryFilter;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PsychologySkillManifest> manifests() { return registry.list(); }

    @Override
    public List<PsychologySkillRunVO> runs(Long userId) {
        return mapper.selectList(new QueryWrapper<PsychologySkillRun>()
                        .eq("user_id", userId).orderByDesc("created_at", "id"))
                .stream().map(run -> toVo(run, parseResult(run.resultJson))).toList();
    }

    @Override
    @Transactional
    public PsychologySkillRunVO run(Long userId, String skillId, PsychologySkillRunRequest request) {
        PsychologySkillManifest manifest;
        try {
            manifest = registry.require(skillId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "这项反思能力不存在或已下线");
        }
        validateRequest(manifest, request);
        PsychologySkillRelease release = releaseService.requireRunnable(manifest.id, manifest.version);
        Map<String, String> answers = normalize(request.answers);
        String joined = String.join("\n", answers.values());
        SafetyResult safety = safetyService.check(joined, userId, null);

        PsychologySkillRun run = baseRun(userId, manifest, release, request, answers);
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            run.status = "ESCALATED";
            run.retentionChoice = "DISCARD_AFTER_SESSION";
            run.inputFingerprint = sha256("ESCALATED_REDACTED:" + UUID.randomUUID());
            run.escalationCode = "LOCAL_CRISIS_RESOURCES";
            mapper.insert(run);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", safety.safeMessage == null ? "先暂停这项练习，把安全和现实支持放在第一位。" : safety.safeMessage);
            result.put("alternatives", safetyService.resources());
            result.put("uncertainty", "这不是诊断；系统只根据当前输入触发安全升级。 ");
            return toVo(run, result);
        }

        Map<String, Object> result = evaluate(skillId, answers, request.locale, request.retentionChoice);
        run.status = "COMPLETED";
        if (!"DISCARD_AFTER_SESSION".equals(request.retentionChoice)) {
            run.resultJson = write(result);
        }
        mapper.insert(run);
        return toVo(run, result);
    }

    @Override
    @Transactional
    public PsychologySkillRunVO revoke(Long userId, Long runId) {
        PsychologySkillRun run = mapper.selectOne(new QueryWrapper<PsychologySkillRun>()
                .eq("id", runId).eq("user_id", userId));
        if (run == null) throw new BusinessException(ErrorCode.NOT_FOUND, "没有找到这次属于你的 Skill 记录");
        run.status = "REVOKED";
        run.resultJson = null;
        run.retentionChoice = "DISCARD_AFTER_SESSION";
        run.revokedAt = LocalDateTime.now();
        mapper.update(null, new UpdateWrapper<PsychologySkillRun>()
                .eq("id", run.id)
                .eq("user_id", userId)
                .set("status", run.status)
                .set("result_json", null)
                .set("retention_choice", run.retentionChoice)
                .set("revoked_at", run.revokedAt));
        return toVo(run, Map.of());
    }

    @Override
    public PsychologySkillSuggestionVO suggest(Long userId, String text, String locale) {
        if (text == null || text.isBlank() || text.length() > ANSWER_MAX_CHARS) return null;
        if (safetyBoundaryFilter.inspect(text).matched) return null;
        String normalized = text.toLowerCase();
        String skillId = null;
        String cue = null;
        if (containsAny(normalized, "纠结", "拉扯", "要不要", "决定", "decision", "conflicted")) {
            skillId = "decision-conflict-map";
            cue = "你刚才明确提到了一个正在拉扯的决定";
        } else if (containsAny(normalized, "选择", "重要", "价值", "option", "choice", "value")) {
            skillId = "values-compass";
            cue = "你刚才明确提到了选择和在意的东西";
        } else if (containsAny(normalized, "感受", "情绪", "紧张", "害怕", "难受", "feeling", "nervous", "afraid")) {
            skillId = "emotion-needs-clarifier";
            cue = "你刚才明确说到一种感受";
        }
        if (skillId == null) return null;
        PsychologySkillManifest manifest = registry.require(skillId);
        releaseService.requireRunnable(skillId, manifest.version);
        PsychologySkillSuggestionVO suggestion = new PsychologySkillSuggestionVO();
        suggestion.skillId = skillId;
        suggestion.skillVersion = manifest.version;
        suggestion.title = manifest.title.getOrDefault(locale, manifest.title.get("zh-CN"));
        suggestion.reason = cue + "。如果你愿意，可以打开这项反思；现在不会读取其他记忆，也不会自动运行。";
        return suggestion;
    }

    private void validateRequest(PsychologySkillManifest manifest, PsychologySkillRunRequest request) {
        if (!request.explicitConsent) throw new BusinessException(ErrorCode.BAD_REQUEST, "必须明确同意后才能开始");
        if (!RETENTION.contains(request.retentionChoice) || !manifest.retentionChoices.contains(request.retentionChoice))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的结果保留方式");
        if (!LOCALES.contains(request.locale)) throw new BusinessException(ErrorCode.BAD_REQUEST, "暂不支持该语言");
        if (request.consentScopes == null || !request.consentScopes.containsAll(manifest.requiredScopes)
                || request.consentScopes.stream().anyMatch(scope -> !manifest.requiredScopes.contains(scope)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同意范围与 Skill manifest 不一致");
        if (request.answers == null || !request.answers.keySet().containsAll(manifest.requiredInputs)
                || request.answers.keySet().stream().anyMatch(key -> !manifest.requiredInputs.contains(key)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请完成所有必填问题");
        for (String key : manifest.requiredInputs) {
            String value = request.answers.get(key);
            if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请完成所有必填问题");
        }
        if (request.answers.values().stream().anyMatch(value -> value != null && value.length() > ANSWER_MAX_CHARS))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单项回答不能超过 " + ANSWER_MAX_CHARS + " 字");
    }

    private PsychologySkillRun baseRun(Long userId, PsychologySkillManifest manifest, PsychologySkillRelease release,
                                       PsychologySkillRunRequest request, Map<String, String> answers) {
        PsychologySkillRun run = new PsychologySkillRun();
        run.userId = userId;
        run.skillId = manifest.id;
        run.skillVersion = manifest.version;
        run.releaseId = release.id;
        run.manifestHash = release.manifestHash;
        run.locale = request.locale;
        run.riskTier = manifest.riskTier;
        run.retentionChoice = request.retentionChoice;
        run.consentScopes = String.join(",", request.consentScopes);
        run.inputFingerprint = sha256(write(new TreeMap<>(answers)));
        run.evidenceRefs = write(manifest.evidence);
        return run;
    }

    private Map<String, String> normalize(Map<String, String> input) {
        Map<String, String> normalized = new LinkedHashMap<>();
        input.forEach((key, value) -> normalized.put(key, value == null ? "" : value.trim()));
        return normalized;
    }

    private Map<String, Object> evaluate(String skillId, Map<String, String> a, String locale, String retentionChoice) {
        boolean english = "en-SG".equals(locale);
        Map<String, Object> result = new LinkedHashMap<>();
        if ("emotion-needs-clarifier".equals(skillId)) {
            result.put("summary", english
                    ? "Your words currently support seeing “" + a.get("feeling") + "” in the situation, with a need for “" + a.get("need") + "”."
                    : "你提供的信息目前更支持：在「" + a.get("situation") + "」里，你感到「" + a.get("feeling") + "」，更想保护的需要是「" + a.get("need") + "」。");
            result.put("alternative", english ? "Another feeling or need may also be present; this wording is yours to revise."
                    : "也可能还有另一种感受或需要同时存在；这些词由你决定，随时可以改。 ");
            result.put("smallAction", english ? "Try one sentence: “Right now I need…, so I will ask for…”"
                    : "试着补完一句：『此刻我需要……，所以我想向……提出……』");
        } else if ("values-compass".equals(skillId)) {
            result.put("summary", english ? "The tension is not simply A versus B; both may be protecting “" + a.get("important") + "” in different ways."
                    : "这不只是 A 和 B 的胜负：两个选择可能都在用不同方式保护「" + a.get("important") + "」。");
            result.put("alternative", english ? "A value can matter without dictating one correct choice. Costs and context still count."
                    : "重视一种价值，不等于它自动给出唯一正确答案；现实代价和情境同样重要。 ");
            result.put("smallAction", english ? "Name one reversible step that gathers information without locking in either option."
                    : "为两个选择各写一个可逆、能增加信息但不立即锁死决定的小尝试。 ");
        } else {
            result.put("summary", english ? "One part moves towards “" + a.get("pullToward") + "”, while another protects you from “" + a.get("pullAway") + "”."
                    : "这份拉扯里，一股力量把你推向「" + a.get("pullToward") + "」，另一股力量在保护你避开「" + a.get("pullAway") + "」。");
            result.put("alternative", english ? "Neither side has to be treated as irrational; each may hold useful information."
                    : "两边都不必被判定为『不理性』，它们可能各自带着有用信息。 ");
            result.put("smallAction", english ? "Choose a small, reversible test and decide in advance what evidence would help."
                    : "选择一个小而可逆的实验，并先写下：看到什么证据会让你更靠近或远离这个决定。 ");
        }
        result.put("confidence", "REFLECTIVE_NOT_DIAGNOSTIC");
        result.put("profileEffect", "PROFILE_ELIGIBLE".equals(retentionChoice)
                ? "requires-separate-confirmation" : "none");
        result.put("nextOptions", List.of("CONTINUE_WITH_AURORA", "SAVE_IF_CONSENTED", "TRY_SMALL_ACTION"));
        return result;
    }

    private PsychologySkillRunVO toVo(PsychologySkillRun run, Map<String, Object> result) {
        PsychologySkillRunVO vo = new PsychologySkillRunVO();
        vo.id = run.id; vo.skillId = run.skillId; vo.skillVersion = run.skillVersion;
        vo.releaseId = run.releaseId; vo.manifestHash = run.manifestHash;
        vo.locale = run.locale; vo.status = run.status; vo.riskTier = run.riskTier;
        vo.retentionChoice = run.retentionChoice;
        vo.consentScopes = run.consentScopes == null || run.consentScopes.isBlank()
                ? List.of() : List.of(run.consentScopes.split(","));
        vo.result = result; vo.evidence = parseList(run.evidenceRefs);
        vo.escalationCode = run.escalationCode; vo.createdAt = run.createdAt; vo.revokedAt = run.revokedAt;
        return vo;
    }

    private Map<String, Object> parseResult(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { return Map.of(); }
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { return List.of(); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Skill serialization failed", exception); }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private boolean containsAny(String text, String... cues) {
        for (String cue : cues) if (text.contains(cue)) return true;
        return false;
    }
}
