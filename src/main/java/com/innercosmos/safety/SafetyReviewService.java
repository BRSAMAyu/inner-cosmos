package com.innercosmos.safety;

import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.mapper.SafetyEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Async LLM re-check service for ambiguous safety cases.
 * Provides second-level safety verification using real LLM analysis.
 *
 * Used when initial keyword-based safety check returns MEDIUM level,
 * allowing the system to verify ambiguous content with semantic understanding.
 */
@Service
public class SafetyReviewService {

    private static final Logger log = LoggerFactory.getLogger(SafetyReviewService.class);

    private final StructuredAiService structuredAiService;
    private final SafetyEventMapper safetyEventMapper;
    private final Executor asyncExecutor;

    public SafetyReviewService(StructuredAiService structuredAiService,
                               SafetyEventMapper safetyEventMapper,
                               Executor asyncExecutor) {
        this.structuredAiService = structuredAiService;
        this.safetyEventMapper = safetyEventMapper;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Perform async LLM re-check on ambiguous safety cases.
     * Returns CompletableFuture that can be processed asynchronously.
     *
     * @param userId User ID
     * @param text Text to re-check
     * @param initialMatch Initial safety match from keyword rules
     * @return CompletableFuture with updated SafetyMatch
     */
    public CompletableFuture<SafetyMatch> asyncRecheck(Long userId, String text, SafetyMatch initialMatch) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Performing LLM safety re-check for userId={}, text={}", userId,
                    text.length() > 50 ? text.substring(0, 50) + "..." : text);

                // Call LLM for safety analysis
                String prompt = """
                    Analyze the following text for safety risks:
                    - Self-harm or suicide ideation
                    - Violence or threats against others
                    - Harassment, abuse, or privacy violation
                    - Any other harmful content

                    Text: """ + text + """

                    Return JSON with:
                    - riskLevel: "HIGH", "MEDIUM", or "LOW"
                    - riskType: specific risk category
                    - explanation: brief reasoning
                    - requiresBlock: true/false

                    Be conservative. If uncertain, flag as HIGH.
                    """;

                // Note: This would use a real LLM call in production
                // For Mock mode, this will use the fallback which returns conservative defaults
                var result = structuredAiService.call(userId, "SAFETY_REVIEW", prompt,
                    Map.of("text", text),
                    SafetyReviewResult.class,
                    () -> fallbackSafetyReview(text, initialMatch));

                // Update risk level based on LLM review
                String newRiskLevel = determineFinalLevel(initialMatch.riskLevel, result.riskLevel);

                // Record re-check result
                recordRecheck(userId, text, initialMatch, result);

                // Create updated SafetyMatch
                SafetyMatch updatedMatch;
                if ("HIGH".equals(newRiskLevel)) {
                    updatedMatch = SafetyMatch.hitWithLlmReview(
                        result.riskType != null ? result.riskType : initialMatch.riskType,
                        "HIGH",
                        initialMatch.matchedRule + " + LLM_REVIEW",
                        "BLOCKED",
                        result.explanation
                    );
                } else {
                    updatedMatch = SafetyMatch.hitWithLlmReview(
                        initialMatch.riskType,
                        newRiskLevel,
                        initialMatch.matchedRule + " + LLM_CLEARED",
                        initialMatch.handledAction,
                        result.explanation
                    );
                }

                log.debug("LLM re-check completed: {} -> {}", initialMatch.riskLevel, newRiskLevel);
                return updatedMatch;

            } catch (Exception e) {
                log.warn("LLM safety re-check failed for userId={}, error: {}", userId, e.getMessage());
                // On failure, return conservative result (keep initial match)
                return initialMatch;
            }
        }, asyncExecutor);
    }

    /**
     * Fallback safety review for Mock mode.
     * Returns conservative HIGH for crisis-like content, MEDIUM for ambiguous.
     */
    private SafetyReviewResult fallbackSafetyReview(String text, SafetyMatch initialMatch) {
        SafetyReviewResult result = new SafetyReviewResult();

        // Check for severe patterns using existing keyword detection
        if (text.contains("我希望我从没出生") || text.contains("我想消失") ||
            text.contains("活着好累") || text.contains("活不下去")) {
            result.riskLevel = "HIGH";
            result.riskType = "CRISIS_KEYWORD";
            result.explanation = "Detected severe distress indicators";
            result.requiresBlock = true;
        } else if (text.contains("威胁") || text.contains("骚扰") || text.contains("人肉")) {
            result.riskLevel = "HIGH";
            result.riskType = "ABUSE";
            result.explanation = "Detected abuse/harassment keywords";
            result.requiresBlock = true;
        } else {
            result.riskLevel = initialMatch.riskLevel;
            result.riskType = initialMatch.riskType;
            result.explanation = "No additional risk detected beyond keyword match";
            result.requiresBlock = !"LOW".equals(initialMatch.riskLevel);
        }

        return result;
    }

    /**
     * Determine final risk level combining initial match and LLM review.
     * LLM review can escalate but not de-severe HIGH risks.
     */
    private String determineFinalLevel(String initialLevel, String llmLevel) {
        if ("HIGH".equals(llmLevel)) return "HIGH";
        if ("HIGH".equals(initialLevel)) return "HIGH"; // Never downgrade HIGH
        if ("MEDIUM".equals(llmLevel)) return "MEDIUM";
        return "LOW";
    }

    /**
     * Record LLM re-check result in SafetyEvent.
     */
    private void recordRecheck(Long userId, String text, SafetyMatch initialMatch, SafetyReviewResult result) {
        SafetyEvent event = new SafetyEvent();
        event.userId = userId;
        event.riskType = "LLM_REVIEW:" + result.riskType;
        event.riskLevel = result.riskLevel;
        event.matchedRule = initialMatch.matchedRule + " -> " + result.riskLevel;
        event.handledAction = result.requiresBlock ? "BLOCKED" : "ALLOWED";

        // Store explanation in triggerScene field
        event.triggerScene = result.explanation;

        safetyEventMapper.insert(event);
        log.debug("LLM re-check result recorded: riskType={}, riskLevel={}",
            result.riskType, result.riskLevel);
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
