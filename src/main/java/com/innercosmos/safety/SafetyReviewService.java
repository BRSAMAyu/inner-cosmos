package com.innercosmos.safety;

import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.mapper.SafetyEventMapper;
import com.innercosmos.util.SafetyTextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Synchronous LLM re-check service for implicit/ambiguous safety cases.
 *
 * The explicit crisis-keyword path is synchronous (it must block the response before
 * it is sent). An asynchronous re-check would arrive too late for crisis routing, so
 * this service exposes only {@link #recheckSync}. It reuses the structured-LLM call,
 * the conservative fallback (Mock mode), the level-combination logic, and records the
 * outcome via {@code recordRecheck}.
 *
 * LLM is reached ONLY through {@link StructuredAiService} (project contract).
 */
@Service
public class SafetyReviewService {

    private static final Logger log = LoggerFactory.getLogger(SafetyReviewService.class);

    private final StructuredAiService structuredAiService;
    private final SafetyEventMapper safetyEventMapper;
    private final Executor reviewExecutor;
    private final long budgetSeconds;

    /** M-003: hard deadline (seconds) for the synchronous safety recheck. A person in
     *  distress must never wait on provider timeouts × JSON-repair retries (20–30s each).
     *  When the budget is exceeded, the deterministic fallback decides immediately. */
    static final long SAFETY_REVIEW_BUDGET_SECONDS = 4L;

    @Autowired
    public SafetyReviewService(StructuredAiService structuredAiService,
                               SafetyEventMapper safetyEventMapper,
                               @Qualifier("aiExecutor") Executor reviewExecutor) {
        this(structuredAiService, safetyEventMapper, reviewExecutor, SAFETY_REVIEW_BUDGET_SECONDS);
    }

    /** Test-friendly constructor with an explicit deadline budget. */
    SafetyReviewService(StructuredAiService structuredAiService,
                        SafetyEventMapper safetyEventMapper,
                        Executor reviewExecutor,
                        long budgetSeconds) {
        this.structuredAiService = structuredAiService;
        this.safetyEventMapper = safetyEventMapper;
        this.reviewExecutor = reviewExecutor;
        this.budgetSeconds = budgetSeconds;
    }

    /**
     * Perform a SYNCHRONOUS LLM re-check on text that carried a distress signal but
     * no explicit crisis keyword. The caller decides blocking based on the returned
     * {@link SafetyMatch}. The review is recorded as an {@code LLM_REVIEW} SafetyEvent.
     *
     * @param userId       User ID
     * @param text         Text to re-check
     * @param initialMatch Initial safety match from keyword rules (may be {@link SafetyMatch#safe()})
     * @return Updated {@link SafetyMatch} carrying the final level and reasoning
     */
    @Transactional(rollbackFor = Exception.class)
    public SafetyMatch recheckSync(Long userId, String text, SafetyMatch initialMatch) {
        try {
            // Gemini audit 3.7 (CONFIRMED/P0): raw text (even truncated) must never enter a
            // normal application log -- this line used to include up to 50 raw characters of
            // exactly the cohort of messages most likely to contain sensitive crisis content.
            // Log only the length; the classification outcome is what recordRecheck persists.
            log.debug("Performing synchronous LLM safety re-check for userId={}, textLength={}",
                    userId, text == null ? 0 : text.length());

            String prompt = """
                    Analyze the following Chinese text for crisis risk ONLY in the sense of
                    imminent self-harm or suicide intent. This is NOT about being tired, stressed,
                    sad, or venting after a hard day — those are normal and must stay LOW/MEDIUM.
                    Do not diagnose. Do not medicalize ordinary distress.

                    Return JSON with:
                    - riskLevel: "HIGH" (only for genuine crisis intent), "MEDIUM", or "LOW"
                    - riskType: short category, e.g. "DISTRESS", "CRISIS_KEYWORD", "NONE"
                    - explanation: one-sentence reasoning in Chinese
                    - requiresBlock: true only when this is a genuine crisis

                    Be conservative: when genuinely uncertain between crisis and venting,
                    flag as HIGH. But tired/stressed hyperbole ("累死了", "困死了", "加班累")
                    is NOT a crisis and must be LOW or MEDIUM.

                    Text: """ + text;

            SafetyReviewResult result;
            try {
                // M-003: bound the synchronous recheck so a person in distress is never held
                // waiting on provider timeouts × JSON-repair retries. On timeout/failure the
                // deterministic fallback decides immediately; the acute-crisis floor below
                // still applies to whatever result we end up with.
                result = CompletableFuture.supplyAsync(
                        () -> structuredAiService.call(userId, "SAFETY_REVIEW", prompt,
                                Map.of("text", text),
                                SafetyReviewResult.class,
                                () -> fallbackSafetyReview(text, initialMatch)),
                        reviewExecutor).get(budgetSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Safety recheck exceeded {}s budget for userId={}; using deterministic fallback",
                        budgetSeconds, userId);
                result = fallbackSafetyReview(text, initialMatch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Safety recheck interrupted for userId={}; using fallback", userId);
                result = fallbackSafetyReview(text, initialMatch);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.warn("Safety recheck async call failed for userId={}; using fallback: {}",
                        userId, cause.getMessage());
                result = fallbackSafetyReview(text, initialMatch);
            }

            // Deterministic acute-crisis floor on the LIVE-LLM success path (RT-002 open_risk #1):
            // a permissive/cleared live verdict (LOW/MEDIUM) must NOT override a genuine acute
            // crisis indicator. This mirrors the fallback (F1) and catch (F9) floors, so the
            // acute-crisis floor is enforced regardless of which path produced the verdict.
            // Rigid BLOCKING stays NARROW here — acute-crisis only (user vision 2026-06-16);
            // general distress/hopelessness is left to the LLM/fallback judgment (perception tier).
            if (looksLikeGenuineCrisis(text) && !"HIGH".equals(result.riskLevel)) {
                result.riskLevel = "HIGH";
                result.requiresBlock = true;
            }

            String newRiskLevel = determineFinalLevel(initialMatch.riskLevel, result.riskLevel);

            recordRecheck(userId, initialMatch, result, newRiskLevel);

            String resolvedType = (result.riskType != null && !result.riskType.isBlank())
                    ? result.riskType
                    : (initialMatch.riskType != null ? initialMatch.riskType : "DISTRESS");
            String baseRule = (initialMatch.matchedRule != null && !initialMatch.matchedRule.isBlank())
                    ? initialMatch.matchedRule
                    : "DISTRESS_SIGNAL";

            if ("HIGH".equals(newRiskLevel)) {
                return SafetyMatch.hitWithLlmReview(
                        resolvedType, "HIGH", baseRule + " + LLM_REVIEW", "RESOURCE_PAGE", result.explanation);
            }
            return SafetyMatch.hitWithLlmReview(
                    resolvedType, newRiskLevel, baseRule + " + LLM_CLEARED", initialMatch.handledAction, result.explanation);
        } catch (Exception e) {
            log.warn("Synchronous LLM safety re-check failed for userId={}; keeping conservative initial match: {}",
                    userId, e.getMessage());
            // Never silently allow ambiguous distress on failure: escalate conservatively.
            // F9: crisis-bearing distress on a re-check failure must BLOCK (HIGH), not be
            // allowed as MEDIUM. Only genuinely ambiguous distress (no crisis indicator)
            // stays at a non-blocking MEDIUM flag.
            String conservativeLevel = looksLikeGenuineCrisis(text)
                    ? "HIGH"
                    : (initialMatch.riskLevel == null || "LOW".equals(initialMatch.riskLevel)
                        ? "MEDIUM" : initialMatch.riskLevel);
            String conservativeAction = "HIGH".equals(conservativeLevel)
                    ? "RESOURCE_PAGE" : initialMatch.handledAction;
            return SafetyMatch.hitWithLlmReview(
                    initialMatch.riskType != null ? initialMatch.riskType : "DISTRESS",
                    conservativeLevel,
                    "DISTRESS_SIGNAL + LLM_UNAVAILABLE",
                    conservativeAction, "Re-check unavailable; kept conservative");
        }
    }

    /**
     * Fallback safety review for Mock mode (or LLM failure).
     * Conservative: HIGH only when the text shows genuine crisis intent beyond mere
     * distress words; ordinary tired/stressed venting stays LOW/MEDIUM and is not
     * medicalized.
     */
    private SafetyReviewResult fallbackSafetyReview(String text, SafetyMatch initialMatch) {
        SafetyReviewResult result = new SafetyReviewResult();

        // F1: Genuine crisis intent is checked FIRST. A casual token (加班/累死了/…)
        // must NEVER amnesty a genuine-crisis indicator. Crisis safety is additive and
        // conservative-on-failure (vision §8.5).
        if (looksLikeGenuineCrisis(text)) {
            result.riskLevel = "HIGH";
            result.riskType = "CRISIS_KEYWORD";
            result.explanation = "识别到明确的自我伤害或告别意图";
            result.requiresBlock = true;
            return result;
        }

        // Only if NOT genuine crisis: casual venting / hyperbole → explicitly cleared
        // (do not medicalize). Vision §9/§13.
        if (isCasualVenting(text)) {
            result.riskLevel = "LOW";
            result.riskType = "DISTRESS";
            result.explanation = "看起来是日常的疲惫或情绪宣泄，并未识别到危机意图";
            result.requiresBlock = false;
            return result;
        }

        // Ambiguous distress → keep/escalate to MEDIUM, flag but do not block.
        // Intended (vision §9 + user clarification 2026-06-16): despair-without-intent
        // (绝望/熬不下去/撑不下去/没意义 etc.) resolves here to a non-blocking MEDIUM. Aurora
        // perceives and comforts this; it is NOT medicalized into a rigid block.
        result.riskLevel = "MEDIUM";
        result.riskType = "DISTRESS";
        result.explanation = "存在情绪困扰信号，但未达到危机程度";
        result.requiresBlock = false;
        return result;
    }

    /**
     * Casual venting heuristics: tired / sleepy / overworked hyperbole that must NOT
     * be treated as crisis. This guards the false-positive boundary (vision §9/§13).
     */
    private boolean isCasualVenting(String text) {
        // Gemini audit 3.7 (CONFIRMED/P0): see CrisisKeywordRule for why this normalizes first
        // (normalizeForMatch also lower-cases, so the separate "ddl"/"DDL" literals below are
        // redundant but kept — harmless — rather than removed as an unrelated cleanup).
        String normalized = SafetyTextNormalizer.normalizeForMatch(text);
        return normalized.contains("加班") || normalized.contains("累死了") || normalized.contains("困死了")
                || normalized.contains("困得要死") || normalized.contains("忙死了") || normalized.contains("烦死了")
                || normalized.contains("工作太") || normalized.contains("不想上班") || normalized.contains("不想上班了")
                || normalized.contains("考试") || normalized.contains("作业") || normalized.contains("ddl")
                || normalized.contains("DDL") || normalized.contains("压力大");
    }

    /**
     * Genuine crisis intent heuristics (beyond distress words). Used by the fallback AND by
     * the live-LLM success-path floor. Keep narrow — the explicit keyword rule already handles
     * the direct terms, and rigid BLOCKING here is reserved for the acute-crisis floor only
     * (user vision 2026-06-16): general distress/hopelessness is handled by Aurora's perception
     * tier, not by this rigid floor.
     *
     * NOTE (intended limits, documented per vision §9 + user clarification):
     *  - Despair-without-intent (绝望/熬不下去/撑不下去/没意义) is deliberately NOT a floor
     *    trigger here: it resolves to a non-blocking MEDIUM on purpose. Aurora perceives and
     *    comforts; it does not medicalize ordinary hopelessness.
     *  - Means/scene planning with no other signal (e.g. "药都准备好了", "天台上") is a known
     *    limit of this deterministic tier and belongs to the Aurora conversational-perception
     *    workstream (handled semantically by the LLM recheck / fallback, not by this floor).
     */
    private boolean looksLikeGenuineCrisis(String text) {
        // Gemini audit 3.7 (CONFIRMED/P0): see CrisisKeywordRule for why this normalizes first
        // (normalizeForMatch already lower-cases, so the separate English-lowering step this
        // method used to do on its own is no longer needed).
        String normalized = SafetyTextNormalizer.normalizeForMatch(text);
        boolean englishCrisis = normalized.contains("kill myself") || normalized.contains("end my life")
                || normalized.contains("want to die") || normalized.contains("suicide");
        return normalized.contains("告别这个世界") || normalized.contains("告别一切")
                || normalized.contains("离开这个世界")
                || normalized.contains("最后的话")
                || normalized.contains("一了百了") || normalized.contains("一觉不醒")
                || normalized.contains("不想醒来") || normalized.contains("不想醒过来")
                || normalized.contains("想要解脱") || normalized.contains("希望我消失")
                || normalized.contains("如果我不在了") || normalized.contains("我是累赘")
                || normalized.contains("是个负担") || normalized.contains("我是负担")
                || normalized.contains("拖累大家") || normalized.contains("了断")
                || normalized.contains("想消失")
                || (normalized.contains("活下去的意义") && normalized.contains("没有"))
                // M-020: lethal means / scene planning (acute-crisis floor).
                || normalized.contains("药都准备好了") || normalized.contains("准备好了药")
                || normalized.contains("天台上") || normalized.contains("站在窗边")
                || normalized.contains("写好遗书") || normalized.contains("烧炭")
                || normalized.contains("上吊") || normalized.contains("跳下去")
                || englishCrisis;
    }

    /**
     * Determine final risk level combining initial match and LLM review.
     * LLM review can escalate but never de-severe an explicit HIGH. A distress signal
     * without a keyword has no prior HIGH to protect, so the LLM's judgment governs.
     */
    private String determineFinalLevel(String initialLevel, String llmLevel) {
        if ("HIGH".equals(llmLevel)) return "HIGH";
        if ("HIGH".equals(initialLevel)) return "HIGH"; // never downgrade explicit HIGH
        if ("MEDIUM".equals(llmLevel)) return "MEDIUM";
        if ("MEDIUM".equals(initialLevel)) return "MEDIUM";
        return "LOW";
    }

    /**
     * Record LLM re-check result in SafetyEvent.
     */
    private void recordRecheck(Long userId, SafetyMatch initialMatch, SafetyReviewResult result, String finalLevel) {
        SafetyEvent event = new SafetyEvent();
        event.userId = userId;
        event.riskType = "LLM_REVIEW:" + result.riskType;
        event.riskLevel = finalLevel;
        event.matchedRule = (initialMatch.matchedRule == null ? "DISTRESS_SIGNAL" : initialMatch.matchedRule)
                + " -> " + finalLevel;
        event.handledAction = result.requiresBlock ? "BLOCKED" : "ALLOWED";
        event.triggerScene = result.explanation;
        safetyEventMapper.insert(event);
        log.debug("LLM re-check result recorded: riskType={}, finalLevel={}", result.riskType, finalLevel);
    }

    /**
     * Result class for LLM safety review.
     */
    public static class SafetyReviewResult {
        public String riskLevel;
        public String riskType;
        public String explanation;
        public boolean requiresBlock;
    }
}
