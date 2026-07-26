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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SafetyServiceImpl implements SafetyService {
    /**
     * Crisis resource-page copy. Kept sober and gentle (vision §8/§9): never a medical
     * diagnosis, never a promise to always be present. Routes to real-world support.
     */
    private static final String CRISIS_SAFE_MESSAGE_ZH =
            "你提到的内容触发了一些安全边界。如果你正处于紧急危险中，请立即拨打 110（报警）或 120（急救），" +
            "也可以拨打全国统一心理援助热线 12356。请尽快联系一位现实中可信任的人。";
    private static final String CRISIS_SAFE_MESSAGE_EN_SG =
            "Your safety comes first. If you are in immediate danger in Singapore, call Police 999 " +
            "or Emergency Ambulance 995. You can also call Samaritans of Singapore at 1767, 24 hours a day. " +
            "Please contact someone you trust in the real world now.";
    private static final String MEDIUM_SAFE_MESSAGE_ZH =
            "我会把安全和尊重放在前面，陪你慢一点说清楚。";
    private static final String MEDIUM_SAFE_MESSAGE_EN =
            "I’ll keep safety and respect in view while we slow this down together.";

    private final SafetyEventMapper safetyEventMapper;
    private final SafetyBoundaryFilter safetyBoundaryFilter;
    private final SafetyReviewService safetyReviewService;
    private final DistressSignalDetector distressSignalDetector;
    // Gemini audit 3.9 (CONFIRMED/P1): session-scoped, time-decaying escalation ADDED on top of
    // the existing per-message detectors above -- never replacing or re-deciding their matches.
    private final SessionRiskAggregator sessionRiskAggregator;
    private final boolean semanticRecheckEnabled;
    private final ConcurrentHashMap<CheckKey, CachedCheck> idempotentChecks = new ConcurrentHashMap<>();
    private static final long CHECK_CACHE_TTL_MS = java.time.Duration.ofMinutes(15).toMillis();
    private static final int CHECK_CACHE_SOFT_LIMIT = 10_000;

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
        return resources("zh-CN", "CN");
    }

    @Override
    public List<String> resources(String locale, String region) {
        if (isSingapore(locale, region)) {
            return List.of(
                    "If you are in immediate danger, call Singapore Police at 999.",
                    "For emergency ambulance or fire services, call 995.",
                    "Samaritans of Singapore (SOS) · 24-hour hotline: 1767.",
                    "SOS CareText · 24-hour WhatsApp: 9151 1767.",
                    "Inner Cosmos does not provide a diagnosis and does not replace emergency services, doctors, counsellors or crisis lines."
            );
        }
        return List.of(
                "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。",
                "需要医疗急救，请立即拨打 120。",
                "全国统一心理援助热线：12356。",
                "Inner Cosmos 不提供心理诊断，也不替代医生、咨询师或热线。"
        );
    }

    @Override
    public SafetyResult check(String text, Long userId, Long sessionId) {
        return check(text, userId, sessionId, null, "zh-CN", "CN");
    }

    @Override
    public SafetyResult check(String text, Long userId, Long sessionId, String observationId,
                              String locale, String region) {
        String stableObservationId = normalizeObservationId(observationId);
        if (stableObservationId == null) {
            return checkUncached(text, userId, sessionId, null, locale, region);
        }
        long now = System.currentTimeMillis();
        if (idempotentChecks.size() > CHECK_CACHE_SOFT_LIMIT) {
            idempotentChecks.entrySet().removeIf(
                    entry -> now - entry.getValue().createdAtMs > CHECK_CACHE_TTL_MS);
        }
        String fingerprint = fingerprint(text);
        CheckKey key = new CheckKey(userId, sessionId, stableObservationId, fingerprint,
                normalizeLocale(locale), normalizeRegion(region));
        CachedCheck cached = idempotentChecks.compute(key, (ignored, existing) -> {
            if (existing != null && now - existing.createdAtMs <= CHECK_CACHE_TTL_MS) {
                return existing;
            }
            SafetyResult checked = checkUncached(
                    text, userId, sessionId, stableObservationId + ":" + fingerprint,
                    locale, region);
            return new CachedCheck(copy(checked), now);
        });
        return copy(cached.result);
    }

    private SafetyResult checkUncached(String text, Long userId, Long sessionId, String observationId,
                                       String locale, String region) {
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
            result.safeMessage = crisisSafeMessage(locale, region);
            sessionRiskAggregator.observe(sessionId, observationId, "HIGH", text); // bookkeeping only
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
            result.safeMessage = mediumSafeMessage(locale);
            sessionRiskAggregator.observe(sessionId, observationId, "HIGH", text); // bookkeeping only
            return result;
        }
        if (match.matched) {
            record(userId, sessionId, match.riskType, "MEDIUM", match.matchedRule, "FLAG");
            result.riskLevel = "MEDIUM";
            result.riskType = match.riskType;
            result.matchedRule = match.matchedRule;
            result.handledAction = "FLAG";
            result.blockModelCall = false;
            result.safeMessage = mediumSafeMessage(locale);
            return escalateIfSessionPatternWarrants(
                    userId, sessionId, observationId, text, locale, region, result);
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
                result.safeMessage = crisisSafeMessage(locale, region);
                sessionRiskAggregator.observe(sessionId, observationId, "HIGH", text); // bookkeeping only
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
            return escalateIfSessionPatternWarrants(
                    userId, sessionId, observationId, text, locale, region, result);
        }

        result.riskLevel = "LOW";
        result.riskType = "NONE";
        result.blockModelCall = false;
        return escalateIfSessionPatternWarrants(
                userId, sessionId, observationId, text, locale, region, result);
    }

    /**
     * Gemini audit 3.9 (CONFIRMED/P1): feeds this turn's own (non-HIGH) risk level into the
     * session-scoped rolling aggregator and, if the ACCUMULATED session pattern crosses the
     * escalation threshold, upgrades the result to the same HIGH/block/resource-page response a
     * single explicit crisis signal would get. This never runs for a result that is already HIGH
     * (those paths feed the aggregator directly for bookkeeping and return above, unmodified) --
     * it only ever escalates UP from MEDIUM/LOW/NONE, never overrides an existing HIGH decision.
     */
    private SafetyResult escalateIfSessionPatternWarrants(Long userId, Long sessionId,
                                                          String observationId, String text,
                                                          String locale, String region,
                                                          SafetyResult result) {
        SessionRiskAggregator.Escalation escalation =
                sessionRiskAggregator.observe(sessionId, observationId, result.riskLevel, text);
        if (escalation.escalate()) {
            record(userId, sessionId, "SESSION_ESCALATION", "HIGH", "session-pattern", "RESOURCE_PAGE");
            result.riskLevel = "HIGH";
            result.riskType = "SESSION_ESCALATION";
            result.matchedRule = "session-pattern";
            result.handledAction = "RESOURCE_PAGE";
            result.blockModelCall = true;
            result.safeMessage = crisisSafeMessage(locale, region);
        }
        return result;
    }

    private String crisisSafeMessage(String locale, String region) {
        return isSingapore(locale, region) ? CRISIS_SAFE_MESSAGE_EN_SG : CRISIS_SAFE_MESSAGE_ZH;
    }

    private String mediumSafeMessage(String locale) {
        return locale != null && locale.toLowerCase(java.util.Locale.ROOT).startsWith("en")
                ? MEDIUM_SAFE_MESSAGE_EN : MEDIUM_SAFE_MESSAGE_ZH;
    }

    private boolean isSingapore(String locale, String region) {
        return (region != null && "SG".equalsIgnoreCase(region.trim()))
                || (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en-sg"));
    }

    private String normalizeObservationId(String observationId) {
        if (observationId == null || observationId.isBlank()) {
            return null;
        }
        String normalized = observationId.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private String normalizeLocale(String locale) {
        return locale == null ? "" : locale.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRegion(String region) {
        return region == null ? "" : region.trim().toUpperCase(Locale.ROOT);
    }

    private String fingerprint(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the JVM", impossible);
        }
    }

    private SafetyResult copy(SafetyResult source) {
        SafetyResult target = new SafetyResult();
        target.riskLevel = source.riskLevel;
        target.riskType = source.riskType;
        target.matchedRule = source.matchedRule;
        target.handledAction = source.handledAction;
        target.safeMessage = source.safeMessage;
        target.blockModelCall = source.blockModelCall;
        return target;
    }

    private record CheckKey(Long userId, Long sessionId, String observationId, String fingerprint,
                            String locale, String region) {}

    private record CachedCheck(SafetyResult result, long createdAtMs) {}

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
