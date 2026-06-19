package com.innercosmos.service;

import com.innercosmos.ai.semantic.EmotionInsight;
import com.innercosmos.ai.structured.StructuredAiResults;

/**
 * IC-EMO-001: the single producer of source-tagged {@link EmotionInsight}s and
 * the single writer of enriched EmotionTrace rows.
 *
 * <p>This collapses the previously duplicated emotion-derivation logic (the
 * listener's 6-keyword block and the settlement service's own EmotionTrace
 * insert) into one semantic path with a deterministic lexicon fallback, and one
 * idempotent persistence path that upserts per session so a dialog-finish trace
 * and its later settlement never double-write.
 */
public interface EmotionInsightService {

    /**
     * Analyze free text into an {@link EmotionInsight}. LLM primary path via
     * StructuredAiService("EMOTION_INSIGHT"); on any failure/unparseable result
     * the deterministic lexicon path produces a LEXICON insight. Never returns
     * null.
     */
    EmotionInsight analyze(Long userId, String text);

    /**
     * Idempotently persist an insight as an EmotionTrace.
     * <ul>
     *   <li>{@code sessionId != null}: upsert by {@code (user_id, source_session_id)}
     *       — update the existing row if present, else insert.</li>
     *   <li>{@code sessionId == null} (diary): plain insert.</li>
     * </ul>
     * DB errors are logged and swallowed; this never throws out.
     */
    void writeTrace(Long userId, Long sessionId, EmotionInsight insight);

    /**
     * Adapt an already-produced settlement emotion into an {@link EmotionInsight}
     * (deriving the spectrum) without a second LLM call. analysisSource =
     * "SETTLEMENT".
     */
    EmotionInsight fromSettlement(StructuredAiResults.SettlementResult result);
}
