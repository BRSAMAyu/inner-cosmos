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
import com.innercosmos.vo.SafetyResourceVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;

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
    private static final String GENTLE_CHECK_IN_ZH =
            "我想温和地确认一下：你现在是安全的吗？你可以继续和我说；如果需要，支持资源也在这里，随时可以展开。";
    private static final String GENTLE_CHECK_IN_EN_SG =
            "Can I gently check: are you safe right now? You can keep talking with me, and local support is here if you want to expand it.";
    private static final String GENTLE_CHECK_IN_UNKNOWN =
            "Can I gently check: are you safe right now? 也想温和确认一下：你现在是安全的吗？你可以继续说，也可以按所在地选择支持资源。";
    private static final String UNKNOWN_REGION_SAFE_MESSAGE =
            "Your safety comes first. If you are in immediate danger, contact your local emergency services or go to the nearest emergency department now. " +
            "安全最重要；如果你正处于紧急危险中，请立即联系所在地紧急服务或前往最近的急诊。";

    private final SafetyEventMapper safetyEventMapper;
    private final SafetyBoundaryFilter safetyBoundaryFilter;
    private final SafetyReviewService safetyReviewService;
    private final DistressSignalDetector distressSignalDetector;
    // Gemini audit 3.9 (CONFIRMED/P1): session-scoped, time-decaying escalation ADDED on top of
    // the existing per-message detectors above -- never replacing or re-deciding their matches.
    private final SessionRiskAggregator sessionRiskAggregator;
    private final boolean semanticRecheckEnabled;
    private final ConcurrentHashMap<CheckKey, CompletableFuture<CachedCheck>> idempotentChecks =
            new ConcurrentHashMap<>();
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
        return resourceCatalog(locale, region).stream().map(resource -> resource.label).toList();
    }

    @Override
    public List<SafetyResourceVO> resourceCatalog(String locale, String region) {
        ResourceRegion resolved = resolveRegion(locale, region);
        if (resolved == ResourceRegion.SINGAPORE) {
            return List.of(
                    resource("sg-police", "If you are in immediate danger, call Singapore Police at 999.",
                            "999", "https://www.gov.sg/contact-us/", "SG", "ALL", "24/7", "PHONE", "EMERGENCY"),
                    resource("sg-ambulance", "For emergency ambulance or fire services, call 995.",
                            "995", "https://www.scdf.gov.sg/home/about-scdf/emergency-medical-services", "SG", "ALL", "24/7", "PHONE", "EMERGENCY"),
                    resource("sg-sos", "Samaritans of Singapore (SOS) · 24-hour hotline: 1767.",
                            "1767", "https://www.sos.org.sg/contact-us/", "SG", "ALL", "24/7", "PHONE", "CRISIS_SUPPORT"),
                    resource("sg-sos-caretext", "SOS CareText · 24-hour WhatsApp: 9151 1767.",
                            "91511767", "https://www.sos.org.sg/contact-us/", "SG", "ALL", "24/7", "WHATSAPP", "CRISIS_SUPPORT"),
                    resource("sg-boundary", "Inner Cosmos does not provide a diagnosis and does not replace emergency services, doctors, counsellors or crisis lines.",
                            null, "https://www.sos.org.sg/contact-us/", "SG", "ALL", "ALWAYS", "NOTICE", "PRODUCT_BOUNDARY")
            );
        }
        if (resolved == ResourceRegion.UNKNOWN) {
            return List.of(
                    resource("global-emergency", "If you are in immediate danger, contact your local emergency services or go to the nearest emergency department now.",
                            null, "https://www.who.int/health-topics/suicide", "GLOBAL", "ALL", "24/7", "IN_PERSON", "EMERGENCY"),
                    resource("global-emergency-zh", "如果你正处于紧急危险中，请立即联系所在地紧急服务或前往最近的急诊。",
                            null, "https://www.who.int/zh/health-topics/suicide", "GLOBAL", "ALL", "24/7", "IN_PERSON", "EMERGENCY"),
                    resource("global-boundary", "Inner Cosmos 不提供心理诊断，也不替代当地紧急服务、医生、咨询师或热线。",
                            null, "https://www.who.int/health-topics/suicide", "GLOBAL", "ALL", "ALWAYS", "NOTICE", "PRODUCT_BOUNDARY")
            );
        }
        return List.of(
                resource("cn-police", "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。",
                        "110", "https://english.shanghai.gov.cn/en-EmergencyNumbers/20240104/8eec5a3d2b864187af8f383cc6b94ae5.html", "CN", "ALL", "24/7", "PHONE", "EMERGENCY"),
                resource("cn-medical", "需要医疗急救，请立即拨打 120。",
                        "120", "https://english.shanghai.gov.cn/en-EmergencyNumbers/20240104/8eec5a3d2b864187af8f383cc6b94ae5.html", "CN", "ALL", "24/7", "PHONE", "EMERGENCY"),
                resource("cn-12356", "全国统一心理援助热线：12356。",
                        "12356", "https://www.nhc.gov.cn/yzygj/c100068/202412/49a1a65386cd4be582d4702fd0926ee8.shtml", "CN", "ALL", "LOCAL_SERVICE", "PHONE", "CRISIS_SUPPORT"),
                resource("cn-12355", "青少年心理咨询援助公共平台：12355。",
                        "12355", "https://www.nhc.gov.cn/wjw/jiany/202408/885168a70d824919ae24be148e89d6cd.shtml", "CN", "YOUTH", "LOCAL_SERVICE", "PHONE", "YOUTH_SUPPORT"),
                resource("cn-boundary", "Inner Cosmos 不提供心理诊断，也不替代医生、咨询师或热线。",
                        null, "https://www.nhc.gov.cn/yzygj/c100068/202604/4133f984e77741299f1c4660de1947f0.shtml", "CN", "ALL", "ALWAYS", "NOTICE", "PRODUCT_BOUNDARY")
        );
    }

    private SafetyResourceVO resource(String id, String label, String phone, String authorityUrl,
                                      String region, String audience, String hours,
                                      String channel, String category) {
        return SafetyResourceVO.of(id, label, phone, authorityUrl, "2026-07-27",
                region, audience, hours, channel, category);
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
            return checkUncached(text, userId, sessionId, null, null, locale, region);
        }
        long now = System.currentTimeMillis();
        sweepCheckCache(now);
        String fingerprint = fingerprint(text);
        CheckKey key = new CheckKey(userId, sessionId, stableObservationId, fingerprint,
                normalizeLocale(locale), normalizeRegion(region));
        while (true) {
            CompletableFuture<CachedCheck> existing = idempotentChecks.get(key);
            if (existing != null) {
                CachedCheck cached = await(existing);
                if (now - cached.createdAtMs <= CHECK_CACHE_TTL_MS) {
                    return copy(cached.result);
                }
                idempotentChecks.remove(key, existing);
                continue;
            }
            CompletableFuture<CachedCheck> candidate = new CompletableFuture<>();
            if (idempotentChecks.putIfAbsent(key, candidate) != null) {
                continue;
            }
            // The winner performs DB / synchronous provider work outside any CHM bin lock.
            try {
                SafetyResult checked = checkUncached(
                        text, userId, sessionId, stableObservationId + ":" + fingerprint,
                        stableObservationId, locale, region);
                CachedCheck completed = new CachedCheck(copy(checked), System.currentTimeMillis());
                candidate.complete(completed);
                return copy(completed.result);
            } catch (RuntimeException failure) {
                candidate.completeExceptionally(failure);
                idempotentChecks.remove(key, candidate);
                throw failure;
            }
        }
    }

    private SafetyResult checkUncached(String text, Long userId, Long sessionId,
                                       String observationId, String clientMessageId,
                                       String locale, String region) {
        SafetyResult result = new SafetyResult();
        if (text == null || text.isBlank()) {
            result.riskLevel = "LOW";
            result.riskType = "NONE";
            result.blockModelCall = false;
            result.safetyState = "NORMAL";
            return result;
        }
        SafetyMatch match = safetyBoundaryFilter.inspect(text);
        // Crisis keywords: HIGH risk, block model call (UNCHANGED explicit path)
        if (match.matched && "CRISIS_KEYWORD".equals(match.riskType)) {
            record(userId, sessionId, clientMessageId,
                    match.riskType, "HIGH", match.matchedRule, "RESOURCE_PAGE");
            result.riskLevel = "HIGH";
            result.riskType = match.riskType;
            result.matchedRule = match.matchedRule;
            result.handledAction = "RESOURCE_PAGE";
            result.blockModelCall = true;
            result.safetyState = "HIGH_CONFIRMED";
            result.safeMessage = crisisSafeMessage(locale, region);
            sessionRiskAggregator.observe(sessionId, observationId, "HIGH", text); // bookkeeping only
            return result;
        }
        // Abuse keywords: HIGH risk, but don't block model call (flag only)
        if (match.matched && "ABUSE".equals(match.riskType)) {
            record(userId, sessionId, clientMessageId,
                    match.riskType, "HIGH", match.matchedRule, "FLAG");
            result.riskLevel = "HIGH";
            result.riskType = match.riskType;
            result.matchedRule = match.matchedRule;
            result.handledAction = "FLAG";
            result.blockModelCall = false;
            result.safetyState = "HIGH_CONFIRMED";
            result.safeMessage = mediumSafeMessage(locale);
            sessionRiskAggregator.observe(sessionId, observationId, "HIGH", text); // bookkeeping only
            return result;
        }
        if (match.matched) {
            record(userId, sessionId, clientMessageId,
                    match.riskType, "MEDIUM", match.matchedRule, "FLAG");
            result.riskLevel = "MEDIUM";
            result.riskType = match.riskType;
            result.matchedRule = match.matchedRule;
            result.handledAction = "FLAG";
            result.blockModelCall = false;
            result.safetyState = "DISTRESS_WATCH";
            result.safeMessage = mediumSafeMessage(locale);
            return applySessionState(
                    userId, sessionId, observationId, text, locale, region, result);
        }

        // No explicit rule matched. Check for an implicit distress signal and, if present,
        // ask for a synchronous semantic re-check (genuine crisis vs. casual venting).
        // This never blocks ordinary tired/stressed venting and never medicalizes it.
        if (semanticRecheckEnabled && distressSignalDetector.hasDistressSignal(text)) {
            SafetyMatch review = clientMessageId == null
                    ? safetyReviewService.recheckSync(userId, text, match)
                    : safetyReviewService.recheckSync(userId, text, match, clientMessageId);
            if ("HIGH".equals(review.riskLevel)) {
                // Genuine implicit crisis → same path as explicit crisis (block + resource page).
                record(userId, sessionId, clientMessageId,
                        review.riskType, "HIGH", review.matchedRule, "RESOURCE_PAGE");
                result.riskLevel = "HIGH";
                result.riskType = review.riskType;
                result.matchedRule = review.matchedRule;
                result.handledAction = "RESOURCE_PAGE";
                result.blockModelCall = true;
                result.safetyState = "HIGH_CONFIRMED";
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
            result.safetyState = "MEDIUM".equals(level) ? "DISTRESS_WATCH" : "NORMAL";
            return applySessionState(
                    userId, sessionId, observationId, text, locale, region, result);
        }

        result.riskLevel = "LOW";
        result.riskType = "NONE";
        result.blockModelCall = false;
        result.safetyState = "NORMAL";
        return applySessionState(
                userId, sessionId, observationId, text, locale, region, result);
    }

    /**
     * F3 evidence-state transition. Repeated medium distress may request a gentle, expandable
     * check-in, but never becomes HIGH or blocks Aurora without new acute evidence.
     */
    private SafetyResult applySessionState(Long userId, Long sessionId,
                                           String observationId, String text,
                                           String locale, String region,
                                           SafetyResult result) {
        SessionRiskAggregator.Observation observation =
                sessionRiskAggregator.observeState(sessionId, observationId, result.riskLevel, text);
        result.safetyState = observation.state().name();
        if (observation.gentleCheckIn()) {
            result.riskLevel = "MEDIUM";
            result.riskType = "GENTLE_CHECK_IN";
            result.matchedRule = "session-pattern";
            result.handledAction = "SUPPORT_OFFER";
            result.blockModelCall = false;
            result.safeMessage = gentleCheckInMessage(locale, region);
        }
        return result;
    }

    private String crisisSafeMessage(String locale, String region) {
        return switch (resolveRegion(locale, region)) {
            case SINGAPORE -> CRISIS_SAFE_MESSAGE_EN_SG;
            case CHINA -> CRISIS_SAFE_MESSAGE_ZH;
            case UNKNOWN -> UNKNOWN_REGION_SAFE_MESSAGE;
        };
    }

    private String mediumSafeMessage(String locale) {
        return locale != null && locale.toLowerCase(java.util.Locale.ROOT).startsWith("en")
                ? MEDIUM_SAFE_MESSAGE_EN : MEDIUM_SAFE_MESSAGE_ZH;
    }

    private String gentleCheckInMessage(String locale, String region) {
        return switch (resolveRegion(locale, region)) {
            case SINGAPORE -> GENTLE_CHECK_IN_EN_SG;
            case CHINA -> GENTLE_CHECK_IN_ZH;
            case UNKNOWN -> GENTLE_CHECK_IN_UNKNOWN;
        };
    }

    private ResourceRegion resolveRegion(String locale, String region) {
        String normalizedLocale = normalizeLocale(locale);
        String normalizedRegion = normalizeRegion(region);
        if ("SG".equals(normalizedRegion) && "en-sg".equals(normalizedLocale)) {
            return ResourceRegion.SINGAPORE;
        }
        if ("CN".equals(normalizedRegion) && "zh-cn".equals(normalizedLocale)) {
            return ResourceRegion.CHINA;
        }
        return ResourceRegion.UNKNOWN;
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
        target.safetyState = source.safetyState;
        return target;
    }

    private CachedCheck await(CompletableFuture<CachedCheck> future) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw failure;
        }
    }

    private void sweepCheckCache(long now) {
        if (idempotentChecks.size() <= CHECK_CACHE_SOFT_LIMIT) {
            return;
        }
        idempotentChecks.entrySet().removeIf(entry -> {
            CompletableFuture<CachedCheck> future = entry.getValue();
            if (!future.isDone() || future.isCompletedExceptionally()) {
                return future.isCompletedExceptionally();
            }
            CachedCheck cached = future.getNow(null);
            return cached != null && now - cached.createdAtMs > CHECK_CACHE_TTL_MS;
        });
    }

    private record CheckKey(Long userId, Long sessionId, String observationId, String fingerprint,
                            String locale, String region) {}

    private record CachedCheck(SafetyResult result, long createdAtMs) {}

    private enum ResourceRegion { CHINA, SINGAPORE, UNKNOWN }

    private void record(Long userId, Long sessionId, String clientMessageId,
                        String type, String level, String rule, String action) {
        SafetyEvent event = new SafetyEvent();
        event.userId = userId;
        event.sessionId = sessionId;
        event.clientMessageId = clientMessageId;
        event.safetyScope = clientMessageId == null ? null : "AURORA_INPUT";
        event.riskType = type;
        event.riskLevel = level;
        event.matchedRule = rule;
        event.handledAction = action;
        try {
            safetyEventMapper.insert(event);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            // Cross-Pod duplicate delivery: the first durable safety decision already won.
        }
    }
}
