package com.innercosmos.service.impl;

import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.mapper.SafetyEventMapper;
import com.innercosmos.safety.DistressSignalDetector;
import com.innercosmos.safety.SafetyBoundaryFilter;
import com.innercosmos.safety.SafetyMatch;
import com.innercosmos.safety.SafetyReviewService;
import com.innercosmos.safety.SessionRiskAggregator;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.SafetyResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SafetyServiceImpl implements SafetyService {
    /**
     * Crisis resource-page copy. Kept sober and gentle (vision §8/§9): never a medical
     * diagnosis, never a promise to always be present. Routes to real-world support.
     */
    private static final String CRISIS_SAFE_MESSAGE =
            "你提到的内容触发了一些安全边界。如果你正处于紧急危险中，请立即拨打 110（报警）或 120（急救），" +
            "或 24 小时心理援助热线 010-82951332。你可以先离开屏幕，喝水，呼吸，并联系一个真实的人。";

    private final SafetyEventMapper safetyEventMapper;
    private final SafetyBoundaryFilter safetyBoundaryFilter;
    private final SafetyReviewService safetyReviewService;
    private final DistressSignalDetector distressSignalDetector;
    // Gemini audit 3.9 (CONFIRMED/P1): session-scoped, time-decaying escalation ADDED on top of
    // the existing per-message detectors above -- never replacing or re-deciding their matches.
    private final SessionRiskAggregator sessionRiskAggregator;
    private final boolean semanticRecheckEnabled;

    public SafetyServiceImpl(SafetyEventMapper safetyEventMapper,
                             SafetyBoundaryFilter safetyBoundaryFilter,
                             SafetyReviewService safetyReviewService,
                             DistressSignalDetector distressSignalDetector,
                             SessionRiskAggregator sessionRiskAggregator,
                             @Value("${inner-cosmos.safety.semantic-recheck.enabled:true}") boolean semanticRecheckEnabled) {
        this.safetyEventMapper = safetyEventMapper;
        this.safetyBoundaryFilter = safetyBoundaryFilter;
        this.safetyReviewService = safetyReviewService;
        this.distressSignalDetector = distressSignalDetector;
        this.sessionRiskAggregator = sessionRiskAggregator;
        this.semanticRecheckEnabled = semanticRecheckEnabled;
    }

    @Override
    public void checkText(Long userId, Long sessionId, String text) {
        SafetyResult result = check(text, userId, sessionId);
        if (Boolean.TRUE.equals(result.blockModelCall)) {
            throw new SafetyBlockedException(result.safeMessage == null ? "内容触发安全边界,请先查看支持资源页." : result.safeMessage);
        }
    }

    @Override
    public List<String> resources() {
        // M-002: real, dialable crisis hotlines for a Chinese-speaking user in distress.
        // Each line carries a number so safety-harbor.html can render a tel: link. Never a
        // diagnosis or a promise to always be present — only a bridge to real-world help.
        return List.of(
                "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。",
                "需要医疗急救，请立即拨打 120。",
                "北京心理危机研究与干预中心 · 24 小时心理援助热线：010-82951332。",
                "全国心理援助热线（希望 24）· 24 小时：400-161-9995。",
                "全国公共卫生公益热线：12320（可转接心理援助）。",
                "青少年心理援助热线 · 12355。",
                "Inner Cosmos 不提供心理诊断，也不替代医生、咨询师或热线。"
        );
    }

    @Override
    public SafetyResult check(String text, Long userId, Long sessionId) {
        SafetyResult result = new SafetyResult();
        if (text == null || text.isBlank()) {
            result.riskLevel = "LOW";
            result.riskType = "NONE";
            result.blockModelCall = false;
            return result;
        }
        SafetyMatch match = safetyBoundaryFilter.inspect(text);
        // Crisis keywords: HIGH risk, block model call (UNCHANGED explicit path)
        if (match.matched && "CRISIS_KEYWORD".equals(match.riskType)) {
            record(userId, sessionId, match.riskType, "HIGH", match.matchedRule, "RESOURCE_PAGE");
            result.riskLevel = "HIGH";
            result.riskType = match.riskType;
            result.matchedRule = match.matchedRule;
            result.handledAction = "RESOURCE_PAGE";
            result.blockModelCall = true;
            result.safeMessage = CRISIS_SAFE_MESSAGE;
            sessionRiskAggregator.observe(sessionId, "HIGH", text); // bookkeeping only, already maximal
            return result;
        }
        // Abuse keywords: HIGH risk, but don't block model call (flag only)
        if (match.matched && "ABUSE".equals(match.riskType)) {
            record(userId, sessionId, match.riskType, "HIGH", match.matchedRule, "FLAG");
            result.riskLevel = "HIGH";
            result.riskType = match.riskType;
            result.matchedRule = match.matchedRule;
            result.handledAction = "FLAG";
            result.blockModelCall = false;
            result.safeMessage = "这段内容可能涉及边界或伤害性表达.我会保持克制,并尽量把讨论带回到安全、尊重和现实可行的方向.";
            sessionRiskAggregator.observe(sessionId, "HIGH", text); // bookkeeping only, already maximal
            return result;
        }
        if (match.matched) {
            record(userId, sessionId, match.riskType, "MEDIUM", match.matchedRule, "FLAG");
            result.riskLevel = "MEDIUM";
            result.riskType = match.riskType;
            result.matchedRule = match.matchedRule;
            result.handledAction = "FLAG";
            result.blockModelCall = false;
            result.safeMessage = "这段内容可能涉及边界或伤害性表达.我会保持克制,并尽量把讨论带回到安全、尊重和现实可行的方向.";
            return escalateIfSessionPatternWarrants(userId, sessionId, text, result);
        }

        // No explicit rule matched. Check for an implicit distress signal and, if present,
        // ask for a synchronous semantic re-check (genuine crisis vs. casual venting).
        // This never blocks ordinary tired/stressed venting and never medicalizes it.
        if (semanticRecheckEnabled && distressSignalDetector.hasDistressSignal(text)) {
            SafetyMatch review = safetyReviewService.recheckSync(userId, text, match);
            if ("HIGH".equals(review.riskLevel)) {
                // Genuine implicit crisis → same path as explicit crisis (block + resource page).
                record(userId, sessionId, review.riskType, "HIGH", review.matchedRule, "RESOURCE_PAGE");
                result.riskLevel = "HIGH";
                result.riskType = review.riskType;
                result.matchedRule = review.matchedRule;
                result.handledAction = "RESOURCE_PAGE";
                result.blockModelCall = true;
                result.safeMessage = CRISIS_SAFE_MESSAGE;
                sessionRiskAggregator.observe(sessionId, "HIGH", text); // bookkeeping only, already maximal
                return result;
            }
            // Casual venting / non-crisis distress → allow; do NOT medicalize.
            String level = review.riskLevel != null ? review.riskLevel : "LOW";
            // recheckSync already recorded an LLM_REVIEW event; no second record here.
            result.riskLevel = level;
            result.riskType = review.riskType;
            result.matchedRule = review.matchedRule;
            result.handledAction = "ALLOWED";
            result.blockModelCall = false;
            return escalateIfSessionPatternWarrants(userId, sessionId, text, result);
        }

        result.riskLevel = "LOW";
        result.riskType = "NONE";
        result.blockModelCall = false;
        return escalateIfSessionPatternWarrants(userId, sessionId, text, result);
    }

    /**
     * Gemini audit 3.9 (CONFIRMED/P1): feeds this turn's own (non-HIGH) risk level into the
     * session-scoped rolling aggregator and, if the ACCUMULATED session pattern crosses the
     * escalation threshold, upgrades the result to the same HIGH/block/resource-page response a
     * single explicit crisis signal would get. This never runs for a result that is already HIGH
     * (those paths feed the aggregator directly for bookkeeping and return above, unmodified) --
     * it only ever escalates UP from MEDIUM/LOW/NONE, never overrides an existing HIGH decision.
     */
    private SafetyResult escalateIfSessionPatternWarrants(Long userId, Long sessionId, String text, SafetyResult result) {
        SessionRiskAggregator.Escalation escalation = sessionRiskAggregator.observe(sessionId, result.riskLevel, text);
        if (escalation.escalate()) {
            record(userId, sessionId, "SESSION_ESCALATION", "HIGH", "session-pattern", "RESOURCE_PAGE");
            result.riskLevel = "HIGH";
            result.riskType = "SESSION_ESCALATION";
            result.matchedRule = "session-pattern";
            result.handledAction = "RESOURCE_PAGE";
            result.blockModelCall = true;
            result.safeMessage = CRISIS_SAFE_MESSAGE;
        }
        return result;
    }

    private void record(Long userId, Long sessionId, String type, String level, String rule, String action) {
        SafetyEvent event = new SafetyEvent();
        event.userId = userId;
        event.sessionId = sessionId;
        event.riskType = type;
        event.riskLevel = level;
        event.matchedRule = rule;
        event.handledAction = action;
        safetyEventMapper.insert(event);
    }
}
