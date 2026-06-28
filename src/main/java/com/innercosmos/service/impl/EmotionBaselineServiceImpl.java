package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import com.innercosmos.ai.semantic.EmotionBaseline;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IC-EMO-003 implementation. See {@link EmotionBaselineService}.
 *
 * <p>EWMA recursion (deterministic, no LLM):
 * <pre>
 *   traces sorted by recordDate asc -> intensities x0..x_{n-1}
 *   ewma_0  = x0 ;  var_0 = 0
 *   delta_i = x_i - ewma_{i-1}
 *   ewma_i  = ewma_{i-1} + ALPHA * delta_i
 *   var_i   = (1 - ALPHA) * (var_{i-1} + ALPHA * delta_i^2)   // incremental EWMVar
 *   stability = 1 / (1 + var)                                   // in (0,1], 1 = steady
 * </pre>
 * Dominant emotion = the emotion with the greatest total EWMA recency weight,
 * where the weight of trace i is the same coefficient that multiplies x_i in the
 * closed form of the EWMA (recent traces weigh more).
 */
@Service
public class EmotionBaselineServiceImpl implements EmotionBaselineService {
    private static final Logger log = LoggerFactory.getLogger(EmotionBaselineServiceImpl.class);

    /**
     * EWMA smoothing factor. 0.3 gives recent traces clearly more pull than old
     * ones while still smoothing single-day spikes (a one-off extreme moves the
     * mean by only 0.3 of its gap). Documented constant — keep deterministic.
     */
    public static final double EWMA_ALPHA = 0.3;

    /** Default rolling window in days for {@link #computeBaseline(Long)}. */
    public static final int DEFAULT_WINDOW_DAYS = 30;

    /** Emotion portrait dims the baseline buffers into (Spec §2 / IC-EMO-003). */
    public static final String DIM_EMOTION_PATTERN = "EMOTION_PATTERN";
    public static final String DIM_CURRENT_STATE = "CURRENT_STATE";
    public static final String DIM_ENERGY_RHYTHM = "ENERGY_RHYTHM";

    private final EmotionTraceMapper emotionTraceMapper;
    @Autowired(required = false)
    private UserPortraitService userPortraitService;

    public EmotionBaselineServiceImpl(EmotionTraceMapper emotionTraceMapper,
                                      UserPortraitService userPortraitService) {
        this.emotionTraceMapper = emotionTraceMapper;
        this.userPortraitService = userPortraitService;
    }

    @Override
    public EmotionBaseline computeBaseline(Long userId) {
        return computeBaseline(userId, DEFAULT_WINDOW_DAYS);
    }

    @Override
    public EmotionBaseline computeBaseline(Long userId, int windowDays) {
        if (userId == null) {
            return EmotionBaseline.absent(windowDays);
        }
        List<EmotionTrace> traces;
        try {
            LocalDate since = LocalDate.now().minusDays(Math.max(0, windowDays));
            traces = emotionTraceMapper.selectList(new QueryWrapper<EmotionTrace>()
                    .eq("user_id", userId)
                    .ge("record_date", since)
                    .orderByAsc("record_date")
                    .orderByAsc("id"));
        } catch (Exception e) {
            log.warn("computeBaseline read failed for user={}: {}", userId, e.getMessage());
            return EmotionBaseline.absent(windowDays);
        }
        return computeFromTraces(traces, windowDays);
    }

    @Override
    public EmotionBaseline computeFromTraces(List<EmotionTrace> traces, int windowDays) {
        EmotionBaseline baseline = new EmotionBaseline();
        baseline.windowDays = windowDays;
        if (traces == null || traces.isEmpty()) {
            return EmotionBaseline.absent(windowDays);
        }
        // Defensive deterministic sort by recordDate asc (null dates sort last,
        // stable), then by id asc as a deterministic tie-breaker.
        List<EmotionTrace> ordered = new ArrayList<>(traces);
        ordered.sort(Comparator
                .comparing((EmotionTrace t) -> t.recordDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(t -> t.id == null ? Long.MAX_VALUE : t.id));

        double ewma = 0.0;
        double ewmvar = 0.0;
        boolean first = true;
        int n = ordered.size();
        // Per-emotion accumulated EWMA recency weight (closed-form coefficient).
        Map<String, Double> emotionWeight = new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {
            EmotionTrace t = ordered.get(i);
            double x = t.emotionScore == null ? 0.0 : EmotionTrace.clampScore(t.emotionScore);
            if (first) {
                ewma = x;
                ewmvar = 0.0;
                first = false;
            } else {
                double delta = x - ewma;
                ewma = ewma + EWMA_ALPHA * delta;
                ewmvar = (1.0 - EWMA_ALPHA) * (ewmvar + EWMA_ALPHA * delta * delta);
            }
            // Closed-form EWMA weight of position i (0=oldest .. n-1=newest):
            //   newest:        ALPHA
            //   middle i:      ALPHA * (1-ALPHA)^(n-1-i)
            //   oldest (i==0): (1-ALPHA)^(n-1)
            double weight;
            int age = (n - 1) - i; // 0 for newest
            if (i == 0) {
                weight = Math.pow(1.0 - EWMA_ALPHA, n - 1);
            } else {
                weight = EWMA_ALPHA * Math.pow(1.0 - EWMA_ALPHA, age);
            }
            String emotion = blank(t.emotionName) ? "平静" : t.emotionName.trim();
            emotionWeight.merge(emotion, weight, Double::sum);
        }

        baseline.present = true;
        baseline.sampleCount = n;
        baseline.intensityMean = ewma;
        baseline.intensityVariance = ewmvar;
        baseline.stabilityScore = 1.0 / (1.0 + ewmvar);
        baseline.dominantEmotion = argmaxEmotion(emotionWeight);
        baseline.baselineLabel = buildLabel(baseline);
        return baseline;
    }

    /** Deterministic argmax: highest weight wins; ties broken by emotion name asc. */
    private String argmaxEmotion(Map<String, Double> weights) {
        String best = null;
        double bestW = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            double w = e.getValue();
            if (w > bestW || (w == bestW && (best == null || e.getKey().compareTo(best) < 0))) {
                bestW = w;
                best = e.getKey();
            }
        }
        return best;
    }

    /** Short human-readable summary; deterministic (no LLM). Never null. */
    private String buildLabel(EmotionBaseline b) {
        String emotion = blank(b.dominantEmotion) ? "平静" : b.dominantEmotion;
        String steadiness = b.stabilityScore >= 0.66 ? "总体平稳"
                : (b.stabilityScore >= 0.4 ? "略有起伏" : "波动较大");
        String level = b.intensityMean >= 6.5 ? "偏强"
                : (b.intensityMean <= 3.5 ? "偏弱" : "适中");
        return String.format("近 %d 日%s，主基调「%s」，强度%s（均值 %.1f/10）",
                b.windowDays, steadiness, emotion, level, b.intensityMean);
    }

    @Override
    public EmotionBaseline bridgeToPortrait(Long userId) {
        EmotionBaseline baseline = computeBaseline(userId);
        if (!baseline.present || userPortraitService == null) {
            // Anti-thrash: no baseline (or no portrait service) => never touch portrait.
            return baseline;
        }
        // Confidence scales with sample depth AND stability so a thin / jittery
        // baseline does not slam the portrait. Capped at a calm 0.85.
        double depth = Math.min(1.0, baseline.sampleCount / 7.0);
        double confidence = clamp01(0.25 + 0.6 * depth * baseline.stabilityScore);
        confidence = Math.min(confidence, 0.85);

        List<PortraitDeltas.Delta> deltas = new ArrayList<>();
        deltas.add(patternDelta(baseline, confidence));
        deltas.add(currentStateDelta(baseline, confidence));
        deltas.add(energyDelta(baseline, confidence));
        try {
            userPortraitService.applyDeltas(userId, deltas);
        } catch (Exception e) {
            log.error("bridgeToPortrait applyDeltas failed for user={}: {}", userId, e.getMessage());
        }
        return baseline;
    }

    private PortraitDeltas.Delta patternDelta(EmotionBaseline b, double confidence) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("dominantEmotion", b.dominantEmotion);
        v.put("stability", round2(b.stabilityScore));
        v.put("windowDays", b.windowDays);
        v.put("sampleCount", b.sampleCount);
        v.put("summary", b.baselineLabel);
        return new PortraitDeltas.Delta(DIM_EMOTION_PATTERN, JsonUtils.toJson(v),
                round2(b.stabilityScore), confidence, List.of("baseline"));
    }

    private PortraitDeltas.Delta currentStateDelta(EmotionBaseline b, double confidence) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("emotion", b.dominantEmotion);
        v.put("intensityMean", round2(b.intensityMean));
        v.put("summary", b.baselineLabel);
        return new PortraitDeltas.Delta(DIM_CURRENT_STATE, JsonUtils.toJson(v),
                round2(b.intensityMean / 10.0), confidence, List.of("baseline"));
    }

    private PortraitDeltas.Delta energyDelta(EmotionBaseline b, double confidence) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("intensityMean", round2(b.intensityMean));
        v.put("intensityVariance", round2(b.intensityVariance));
        v.put("stability", round2(b.stabilityScore));
        return new PortraitDeltas.Delta(DIM_ENERGY_RHYTHM, JsonUtils.toJson(v),
                round2(b.intensityMean / 10.0), confidence, List.of("baseline"));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
