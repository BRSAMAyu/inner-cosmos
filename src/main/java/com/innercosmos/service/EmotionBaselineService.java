package com.innercosmos.service;

import com.innercosmos.ai.semantic.EmotionBaseline;
import com.innercosmos.entity.EmotionTrace;

import java.util.List;

/**
 * IC-EMO-003: computes a user's N-day rolling EWMA emotional <b>baseline</b> from
 * {@link EmotionTrace} rows and bridges it — buffered — into the emotion-related
 * user-portrait dimensions.
 *
 * <p>This is the mid/long-term timescale that complements the Phase-2 real-time
 * "此刻情绪" read. The core anti-thrash invariant (Spec §2): individual real-time
 * traces NEVER move the portrait; only a baseline recompute does. So the bridge
 * lives here, not on the per-trace write path.
 *
 * <p>The EWMA math is fully deterministic — identical input rows always produce
 * identical numeric output — and requires no LLM.
 */
public interface EmotionBaselineService {

    /**
     * Compute the user's emotional baseline over the default rolling window from
     * their recent {@link EmotionTrace} rows. Never returns null — an empty history
     * yields a well-formed {@link EmotionBaseline#absent} read.
     */
    EmotionBaseline computeBaseline(Long userId);

    /**
     * Compute the user's emotional baseline over an explicit window of {@code days}.
     * Never returns null.
     */
    EmotionBaseline computeBaseline(Long userId, int windowDays);

    /**
     * Pure, deterministic EWMA core: fold an ordered (any order accepted — sorted
     * internally by recordDate asc) list of traces into a baseline. Exposed for
     * deterministic unit testing of the math without a DB. {@code traces} may be
     * empty (→ absent). Never returns null, never throws.
     */
    EmotionBaseline computeFromTraces(List<EmotionTrace> traces, int windowDays);

    /**
     * Compute the baseline and bridge it into the emotion portrait dims
     * (EMOTION_PATTERN / CURRENT_STATE / ENERGY_RHYTHM) via the buffered
     * {@code UserPortraitService.applyDeltas} path. No-op (no portrait change) when
     * there is no baseline. This is the ONLY path that lets emotion data move the
     * portrait. Returns the baseline that was bridged (or absent).
     */
    EmotionBaseline bridgeToPortrait(Long userId);
}
