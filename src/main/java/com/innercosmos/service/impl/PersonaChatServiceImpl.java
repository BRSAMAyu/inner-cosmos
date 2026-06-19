package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.ai.context.AgentContextAssembler;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.CapsuleUsageQuota;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.CapsuleUsageQuotaMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
import com.innercosmos.service.PersonaChatService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.CapsuleQuotaVO;
import com.innercosmos.vo.SafetyResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PersonaChatServiceImpl implements PersonaChatService {
    /**
     * SEED (official seed capsules) effective daily turn limit.
     * Set to the same clamp ceiling as non-SEED capsules (50) so that:
     *   - the previous bug (SEED=10 < non-SEED default 30, i.e. official seeds were
     *     MORE restricted than user capsules) is fixed;
     *   - official seeds stay the most available, feeling "unlimited" in practice;
     *   - SEED still goes through the same per-day hard quota mechanism as everyone
     *     else (the old session-based bypass is already closed).
     */
    private static final int SEED_EFFECTIVE_DAILY_LIMIT = 50;

    private final PersonaChatSessionMapper sessionMapper;
    private final PersonaChatMessageMapper messageMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleAgent capsuleAgent;
    private final SafetyService safetyService;
    private final StructuredAiService structuredAiService;
    private final CapsuleBoundaryMapper boundaryMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final AgentContextAssembler agentContextAssembler;
    // Read-path mapper for quota state. The WRITE path stays on JdbcTemplate because
    // the atomic conditional UPDATE (turn_count < limit) must be a single SQL statement;
    // MyBatis-Plus BaseMapper cannot express that condition atomically. This asymmetry
    // (read via mapper, write via jdbcTemplate) is intentional.
    private final CapsuleUsageQuotaMapper quotaMapper;
    private final JdbcTemplate jdbcTemplate;

    public PersonaChatServiceImpl(PersonaChatSessionMapper sessionMapper,
                                  PersonaChatMessageMapper messageMapper,
                                  EchoCapsuleMapper capsuleMapper,
                                  CapsuleAgent capsuleAgent,
                                  SafetyService safetyService,
                                  StructuredAiService structuredAiService,
                                  CapsuleBoundaryMapper boundaryMapper,
                                  MemoryCardMapper memoryCardMapper,
                                  AgentContextAssembler agentContextAssembler,
                                  CapsuleUsageQuotaMapper quotaMapper,
                                  JdbcTemplate jdbcTemplate) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.capsuleMapper = capsuleMapper;
        this.capsuleAgent = capsuleAgent;
        this.safetyService = safetyService;
        this.structuredAiService = structuredAiService;
        this.boundaryMapper = boundaryMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.agentContextAssembler = agentContextAssembler;
        this.quotaMapper = quotaMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PersonaChatSession create(Long userId, Long capsuleId) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null) {
            throw new BusinessException("NOT_FOUND", "共鸣体不存在");
        }
        if (!Boolean.TRUE.equals(capsule.isPublic) || !"PUBLIC".equals(capsule.visibilityStatus)) {
            throw new BusinessException("FORBIDDEN", "该共鸣体未公开,无法发起对话");
        }
        PersonaChatSession session = new PersonaChatSession();
        session.visitorUserId = userId;
        session.capsuleId = capsuleId;
        session.status = "ACTIVE";
        session.turnCount = 0;
        boolean isSeed = "SEED_CAPSULE".equals(capsule.capsuleType) || "SEED".equals(capsule.capsuleType);
        session.dailyLimit = isSeed
                ? SEED_EFFECTIVE_DAILY_LIMIT
                : Math.max(2, Math.min(50, capsule.conversationLimitPerDay != null ? capsule.conversationLimitPerDay : 30));
        sessionMapper.insert(session);
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PersonaChatMessage reply(Long userId, Long sessionId, String message) {
        PersonaChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("NOT_FOUND", "persona chat session not found");
        }
        if (!userId.equals(session.visitorUserId)) {
            throw new BusinessException("UNAUTHORIZED", "无权操作此会话");
        }
        SafetyResult safety = safetyService.check(message, userId, null);

        // NOTE: user message is recorded before quota check intentionally — the visitor
        // did compose and send the message. LETTER_GUIDED responses are excluded from
        // meaningful history by downstream filtering (tracked as follow-up IC-CAP-002).
        PersonaChatMessage userMessage = new PersonaChatMessage();
        userMessage.sessionId = sessionId;
        userMessage.senderType = "VISITOR";
        userMessage.textContent = message;
        messageMapper.insert(userMessage);

        PersonaChatMessage capsuleMessage = new PersonaChatMessage();
        capsuleMessage.sessionId = sessionId;
        capsuleMessage.senderType = "CAPSULE";

        // Fetch capsule once, before any branch
        EchoCapsule capsule = capsuleMapper.selectById(session.capsuleId);
        int dailyLimit = resolveDailyLimit(capsule);

        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            capsuleMessage.textContent = safety.safeMessage;
            session.status = "SAFETY_GUIDED";
        } else {
            // Atomically try to reserve a turn before calling AI
            LocalDate today = LocalDate.now();
            boolean reserved = tryReserveQuota(userId, session.capsuleId, today, dailyLimit);

            if (!reserved) {
                capsuleMessage.textContent = "今天的回声已经足够深了.如果你愿意,可以把想继续说的话写成一封慢信.";
                session.status = "LETTER_GUIDED";
            } else {
                String personaName = capsule != null && capsule.pseudonym != null ? capsule.pseudonym : "数字回声";
                String personaIntro = capsule != null && capsule.intro != null ? capsule.intro : "一个有限的共鸣体";
                String personaPrompt = capsule != null && capsule.personaPrompt != null && !capsule.personaPrompt.isBlank()
                        ? capsule.personaPrompt
                        : capsuleAgent.buildPersonaPrompt(personaName, personaIntro);
                CapsuleBoundary boundary = boundary(capsule == null ? null : capsule.id);
                String authorizedSummary = authorizedMemorySummary(capsule);
                AgentContext visitorContext = agentContextAssembler.assemble(userId, null, message, true);
                List<String> history = recentHistory(sessionId);
                Map<String, Object> aiContext = new LinkedHashMap<>();
                aiContext.put("personaPrompt", personaPrompt);
                aiContext.put("authorizedMemorySummary", authorizedSummary);
                aiContext.put("styleProfile", capsule == null ? "" : nullToEmpty(capsule.styleProfileJson));
                aiContext.put("contextPreview", capsule == null ? "" : nullToEmpty(capsule.contextPreviewJson));
                aiContext.put("ownerContextNote", capsule == null ? "" : nullToEmpty(capsule.ownerContextNote));
                aiContext.put("standInEnabled", capsule != null && Boolean.TRUE.equals(capsule.standInEnabled));
                aiContext.put("realContactPolicy", capsule == null ? "LETTER_ONLY" : nullToDefault(capsule.realContactPolicy, "LETTER_ONLY"));
                aiContext.put("boundary", boundary == null ? "" : java.util.Map.of(
                        "allowTopics", nullToEmpty(boundary.allowTopics),
                        "blockedTopics", nullToEmpty(boundary.blockedTopics),
                        "privacyLevel", nullToEmpty(boundary.privacyLevel)));
                aiContext.put("recentPersonaChat", history);
                aiContext.put("visitorContext", visitorContext);
                aiContext.put("visitorMessage", message);
                aiContext.put("turnCount", session.turnCount);
                aiContext.put("dailyLimit", dailyLimit);
                String prefix = "MEDIUM".equals(safety.riskLevel) ? "我会先把这段话放回到安全和尊重的边界里. " : "";
                StructuredAiResults.PersonaResult ai = structuredAiService.call(userId, "PERSONA_CHAT",
                        """
                        只返回 JSON：{"reply":"","boundaryNotice":"","letterSuggested":false,"riskFlags":[]}
                        你正在驱动一个共鸣体，不是真人实时回复，也不是治疗师。
                        必须基于 personaPrompt、authorizedMemorySummary、styleProfile、ownerContextNote 和 boundary 回应。
                        如果 standInEnabled=true，可以说明"我可以先作为回声代你回应"；否则只能引导慢信或真人会话邀请。
                        不要美化原用户；保留真实困惑、表达习惯、价值偏好和边界。
                        不要泄露真实身份、联系方式、原始对话全文和未授权记忆。
                        """,
                        aiContext,
                        StructuredAiResults.PersonaResult.class,
                        () -> unavailablePersona());
                String boundaryText = ai.boundaryNotice == null || ai.boundaryNotice.isBlank() ? "" : ai.boundaryNotice + " ";
                String identityNotice = capsule != null && "USER_CAPSULE".equals(capsule.capsuleType)
                        ? "（这是授权共鸣体的回应，不是真人实时在线。）"
                        : "";
                capsuleMessage.textContent = prefix + boundaryText + blank(ai.reply, "真实模型暂时不可用，我不想用模板伪装成这个共鸣体。请稍后再试，或者写一封慢信。") + identityNotice;

                // Quota already atomically incremented in tryReserveQuota
                session.status = "ACTIVE"; // reset from any prior LETTER_GUIDED
                session.turnCount = session.turnCount == null ? 1 : session.turnCount + 1;
            }
        }
        messageMapper.insert(capsuleMessage);
        sessionMapper.updateById(session);
        return capsuleMessage;
    }

    private int resolveDailyLimit(EchoCapsule capsule) {
        if (capsule == null) return 30;
        boolean isSeed = "SEED_CAPSULE".equals(capsule.capsuleType) || "SEED".equals(capsule.capsuleType);
        if (isSeed) return SEED_EFFECTIVE_DAILY_LIMIT; // SEED effective cap; never override with conversationLimitPerDay
        int configured = capsule.conversationLimitPerDay != null ? capsule.conversationLimitPerDay : 30;
        return Math.max(2, Math.min(50, configured));
    }

    /**
     * Atomically reserves one quota turn for today.
     * Returns true if the quota was successfully reserved (AI should be called).
     * Returns false if the daily limit is already met.
     *
     * Strategy:
     * 1. Try conditional UPDATE: increments only when turn_count < dailyLimit.
     *    If 1 row updated, reserved successfully.
     * 2. If 0 rows updated: either no row exists (first turn) or limit already hit.
     *    Try INSERT (via JdbcTemplate to stay in the same Spring transaction).
     *    If INSERT succeeds, reserved (first turn of the day).
     * 3. If INSERT throws DuplicateKeyException: row exists but turn_count >= dailyLimit.
     *    Return false.
     */
    private boolean tryReserveQuota(Long userId, Long capsuleId, LocalDate today, int dailyLimit) {
        if (dailyLimit <= 0) return false;
        // 1. Try conditional UPDATE: increments only if under limit
        int updated = jdbcTemplate.update(
                "UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1, updated_at = CURRENT_TIMESTAMP " +
                "WHERE visitor_user_id = ? AND capsule_id = ? AND quota_date = ? AND turn_count < ?",
                userId, capsuleId, today, dailyLimit);
        if (updated == 1) return true;
        // 2. No existing row (or limit already hit): try INSERT for first-of-day via JdbcTemplate
        //    (uses the same Spring-managed connection as the outer transaction)
        try {
            jdbcTemplate.update(
                    "INSERT INTO tb_capsule_usage_quota (visitor_user_id, capsule_id, quota_date, turn_count) " +
                    "VALUES (?, ?, ?, 1)",
                    userId, capsuleId, today);
            return true; // inserted first turn
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Row exists but turn_count >= dailyLimit
            return false;
        }
    }

    private StructuredAiResults.PersonaResult unavailablePersona() {
        StructuredAiResults.PersonaResult result = new StructuredAiResults.PersonaResult();
        result.reply = "真实模型暂时不可用，我不想用模板伪装成这个共鸣体。请稍后再试，或者写一封慢信。";
        result.boundaryNotice = "模型状态提示：";
        result.letterSuggested = true;
        result.riskFlags = List.of("REMOTE_UNAVAILABLE");
        return result;
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private CapsuleBoundary boundary(Long capsuleId) {
        if (capsuleId == null) return null;
        return boundaryMapper.selectOne(new QueryWrapper<CapsuleBoundary>().eq("capsule_id", capsuleId).last("LIMIT 1"));
    }

    private String authorizedMemorySummary(EchoCapsule capsule) {
        if (capsule == null || capsule.authorizedMemoryIds == null || capsule.authorizedMemoryIds.isBlank()) return "";
        // Batch fetch: collect IDs first, then single query instead of N+1
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (String raw : capsule.authorizedMemoryIds.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
            try {
                Long id = Long.parseLong(raw.trim());
                ids.add(id);
            } catch (Exception ignored) {}
        }
        if (ids.isEmpty()) return "";
        java.util.List<MemoryCard> cards = memoryCardMapper.selectBatchIds(ids);
        StringBuilder sb = new StringBuilder();
        for (MemoryCard card : cards) {
            sb.append("#").append(card.id).append(" ").append(card.title).append("：")
                    .append(card.summary == null ? "" : card.summary.substring(0, Math.min(card.summary.length(), 180))).append("\n");
        }
        return sb.toString();
    }

    private List<String> recentHistory(Long sessionId) {
        return messageMapper.selectList(new QueryWrapper<PersonaChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByDesc("id")
                        .last("LIMIT 8"))
                .stream()
                .map(m -> m.senderType + "：" + m.textContent)
                .toList();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public List<PersonaChatMessage> messages(Long sessionId) {
        QueryWrapper<PersonaChatMessage> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByAsc("id");
        return messageMapper.selectList(query);
    }

    @Override
    public void verifyOwnership(Long userId, Long sessionId) {
        PersonaChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("NOT_FOUND", "共鸣体对话会话不存在");
        }
        if (!userId.equals(session.visitorUserId)) {
            throw new BusinessException("UNAUTHORIZED", "无权访问此会话");
        }
    }

    @Override
    public CapsuleQuotaVO quota(Long userId, Long capsuleId) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        int dailyLimit = resolveDailyLimit(capsule);
        boolean seed = capsule != null
                && ("SEED_CAPSULE".equals(capsule.capsuleType) || "SEED".equals(capsule.capsuleType));
        LocalDate today = LocalDate.now();
        CapsuleUsageQuota row = quotaMapper.selectOne(
                new QueryWrapper<CapsuleUsageQuota>()
                        .eq("visitor_user_id", userId)
                        .eq("capsule_id", capsuleId)
                        .eq("quota_date", today)
                        .last("LIMIT 1"));
        int turnCount = row != null && row.turnCount != null ? row.turnCount : 0;
        int remaining = Math.max(0, dailyLimit - turnCount);
        return new CapsuleQuotaVO(turnCount, dailyLimit, remaining, seed, today.toString());
    }
}
