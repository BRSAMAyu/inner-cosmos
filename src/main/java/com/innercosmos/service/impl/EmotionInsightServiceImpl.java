package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.innercosmos.ai.semantic.EmotionInsight;
import com.innercosmos.ai.semantic.EmotionSpectrumDeriver;
import com.innercosmos.ai.semantic.EmotionWeatherMapper;
import com.innercosmos.ai.semantic.MomentMood;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer;
import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer.AnalysisResult;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.service.EmotionInsightService;
import com.innercosmos.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IC-EMO-001 implementation. See {@link EmotionInsightService}.
 */
@Service
public class EmotionInsightServiceImpl implements EmotionInsightService {
    private static final Logger log = LoggerFactory.getLogger(EmotionInsightServiceImpl.class);

    private static final String SCENE_KEY = "EMOTION_INSIGHT";

    /** IC-EMO-002: how many spectrum entries the "此刻情绪" label surfaces (top N by ratio). */
    private static final int MOMENT_SPECTRUM_TOP_N = 3;

    /** Reused for defensive spectrum-JSON parsing — same Jackson lib as JsonUtils. */
    private static final ObjectMapper MOMENT_MAPPER = new ObjectMapper();

    private final StructuredAiService structuredAiService;
    private final EmotionTraceMapper emotionTraceMapper;

    public EmotionInsightServiceImpl(StructuredAiService structuredAiService,
                                     EmotionTraceMapper emotionTraceMapper) {
        this.structuredAiService = structuredAiService;
        this.emotionTraceMapper = emotionTraceMapper;
    }

    @Override
    public EmotionInsight analyze(Long userId, String text) {
        String safeText = text == null ? "" : text;
        AnalysisResult analysis = PseudoSemanticAnalyzer.analyze(safeText);

        // The deterministic lexicon Emotion doubles as the fallback. We keep a
        // reference so we can tell whether StructuredAiService actually used the
        // LLM result or fell back to this very instance (prod path), and so the
        // unit test's throwing mock also resolves to LEXICON.
        StructuredAiResults.Emotion lexiconEmotion = lexiconEmotion(analysis);

        StructuredAiResults.Emotion result;
        boolean fromLlm;
        try {
            result = structuredAiService.call(userId, SCENE_KEY,
                    """
                    Analyze the user's emotional state from the text.
                    Return JSON: {emotionName, emotionScore(0-10), weatherType, triggerScene,
                    spectrum:[{emotion, ratio}]}. ratios should sum to about 1.0.
                    Be non-clinical and privacy-preserving.
                    """,
                    Map.of("text", safeText),
                    StructuredAiResults.Emotion.class,
                    () -> lexiconEmotion);
            // If the gateway handed back our own fallback instance, it fell back.
            fromLlm = result != null && result != lexiconEmotion;
        } catch (Exception e) {
            // A throwing gateway (or any unexpected failure) -> deterministic lexicon.
            log.warn("EMOTION_INSIGHT analyze fell back to lexicon: {}", e.getMessage());
            result = lexiconEmotion;
            fromLlm = false;
        }
        if (result == null) {
            result = lexiconEmotion;
            fromLlm = false;
        }

        return toInsight(result, analysis,
                fromLlm ? EmotionInsight.SOURCE_LLM : EmotionInsight.SOURCE_LEXICON);
    }

    @Override
    public EmotionInsight fromSettlement(StructuredAiResults.SettlementResult settlement) {
        StructuredAiResults.Emotion emotion = settlement == null || settlement.emotionTrace == null
                ? new StructuredAiResults.Emotion()
                : settlement.emotionTrace;
        // Settlement is text-derived; re-run the cheap lexicon analyzer over the
        // emotion's trigger scene to seed a spectrum when the LLM didn't emit one.
        String seed = emotion.triggerScene == null ? "" : emotion.triggerScene;
        AnalysisResult analysis = PseudoSemanticAnalyzer.analyze(seed);
        return toInsight(emotion, analysis, EmotionInsight.SOURCE_SETTLEMENT);
    }

    @Override
    public MomentMood latestMood(Long userId) {
        if (userId == null) {
            return MomentMood.absent("此刻还没有读到你的情绪");
        }
        EmotionTrace trace;
        try {
            trace = emotionTraceMapper.selectOne(new QueryWrapper<EmotionTrace>()
                    .eq("user_id", userId)
                    .orderByDesc("record_date")
                    .orderByDesc("id")
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("latestMood read failed for user={}: {}", userId, e.getMessage());
            return MomentMood.absent("此刻还没有读到你的情绪");
        }
        if (trace == null) {
            return MomentMood.absent("此刻还没有读到你的情绪");
        }

        MomentMood mood = new MomentMood();
        mood.present = true;
        mood.primaryEmotion = blank(trace.emotionName) ? "平静" : trace.emotionName.trim();
        mood.intensity = trace.emotionScore == null ? 0.0 : EmotionInsight.clampScore(trace.emotionScore);
        mood.weatherType = blank(trace.weatherType) ? MomentMood.NEUTRAL_WEATHER : trace.weatherType.trim();
        mood.spectrum = parseTopSpectrum(trace.emotionSpectrum);
        mood.momentLabel = buildMomentLabel(mood);
        return mood;
    }

    /**
     * Defensively parse the stored {@code emotionSpectrum} JSON (a list of
     * {emotion, ratio}) into the top-N entries by ratio. Tolerates null / blank /
     * malformed JSON (old Phase-1 rows) by returning an empty list — never throws.
     */
    private List<EmotionInsight.SpectrumEntry> parseTopSpectrum(String json) {
        List<EmotionInsight.SpectrumEntry> out = new ArrayList<>();
        if (blank(json)) {
            return out;
        }
        try {
            JsonNode root = MOMENT_MAPPER.readTree(json);
            if (root == null || !root.isArray()) {
                return out;
            }
            for (JsonNode node : root) {
                String emotion = node.path("emotion").asText(null);
                if (blank(emotion)) {
                    continue;
                }
                double ratio = node.path("ratio").asDouble(0.0);
                out.add(new EmotionInsight.SpectrumEntry(emotion.trim(), ratio));
            }
        } catch (Exception e) {
            // Malformed spectrum -> graceful degrade to emotion-only.
            return new ArrayList<>();
        }
        out.sort((a, b) -> Double.compare(b.ratio, a.ratio));
        if (out.size() > MOMENT_SPECTRUM_TOP_N) {
            return new ArrayList<>(out.subList(0, MOMENT_SPECTRUM_TOP_N));
        }
        return out;
    }

    /**
     * Build the compact "此刻情绪" label: primary emotion, then a brief top-2/3
     * spectrum ("平静 60% · 期待 30%") when available, else emotion-only.
     */
    private String buildMomentLabel(MomentMood mood) {
        String emotion = blank(mood.primaryEmotion) ? "平静" : mood.primaryEmotion;
        if (mood.spectrum == null || mood.spectrum.isEmpty()) {
            return emotion;
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (EmotionInsight.SpectrumEntry e : mood.spectrum) {
            if (e == null || blank(e.emotion)) {
                continue;
            }
            if (shown > 0) {
                sb.append(" · ");
            }
            sb.append(e.emotion).append(' ').append(Math.round(e.ratio * 100)).append('%');
            if (++shown >= 2) {
                break;
            }
        }
        if (shown == 0) {
            return emotion;
        }
        return emotion + "（" + sb + "）";
    }

    @Override
    public void writeTrace(Long userId, Long sessionId, EmotionInsight insight) {
        if (insight == null) {
            return;
        }
        try {
            EmotionTrace trace = sessionId == null
                    ? new EmotionTrace()
                    : findBySession(userId, sessionId);
            boolean update = trace != null && trace.id != null;
            if (trace == null) {
                trace = new EmotionTrace();
            }

            trace.userId = userId;
            trace.sourceSessionId = sessionId;
            trace.emotionName = insight.primaryEmotion;
            trace.emotionScore = EmotionTrace.clampScore(insight.emotionScore);
            trace.weatherType = insight.weatherType;
            trace.triggerScene = insight.triggerScene;
            trace.emotionSpectrum = JsonUtils.toJson(insight.spectrum == null ? List.of() : insight.spectrum);
            trace.analysisSource = insight.analysisSource;
            trace.recordDate = LocalDate.now();

            if (update) {
                trace.updatedAt = LocalDateTime.now();
                emotionTraceMapper.updateById(trace);
            } else {
                trace.createdAt = LocalDateTime.now();
                trace.updatedAt = LocalDateTime.now();
                insertWithRaceFallback(userId, sessionId, trace);
            }
        } catch (Exception e) {
            log.error("writeTrace failed for user={} session={}: {}", userId, sessionId, e.getMessage());
        }
    }

    /**
     * Insert the trace, but recover from the concurrent-insert race: if two
     * DialogFinished listeners both observed no prior row and race to insert, the
     * unique (user_id, source_session_id) guard makes the loser's insert fail with
     * a duplicate-key error. Instead of leaking that error (or, worse, retrying the
     * insert), re-query the winner's row and update it in place so we still end up
     * with exactly one enriched trace. Diary rows (sessionId == null) never hit
     * this path — callers pass a fresh row straight to insert below.
     */
    private void insertWithRaceFallback(Long userId, Long sessionId, EmotionTrace trace) {
        try {
            emotionTraceMapper.insert(trace);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            if (sessionId == null) {
                // Diary inserts must not collide on session; rethrow to outer log.
                throw dup;
            }
            log.warn("writeTrace lost insert race for user={} session={}, recovering via update: {}",
                    userId, sessionId, dup.getMessage());
            EmotionTrace winner = findBySession(userId, sessionId);
            if (winner == null || winner.id == null) {
                // No row to recover onto — propagate so the outer guard logs it.
                throw dup;
            }
            trace.id = winner.id;
            trace.updatedAt = LocalDateTime.now();
            emotionTraceMapper.updateById(trace);
        }
    }

    /** Upsert lookup mirroring the upsertDailyRecord idiom. */
    private EmotionTrace findBySession(Long userId, Long sessionId) {
        QueryWrapper<EmotionTrace> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("source_session_id", sessionId)
                .orderByDesc("id").last("LIMIT 1");
        return emotionTraceMapper.selectOne(query);
    }

    /** Build the deterministic lexicon Emotion that also serves as the fallback. */
    private StructuredAiResults.Emotion lexiconEmotion(AnalysisResult analysis) {
        StructuredAiResults.Emotion emotion = new StructuredAiResults.Emotion();
        emotion.emotionName = emotionName(analysis);
        emotion.emotionScore = analysis.intensityScore;
        emotion.weatherType = EmotionWeatherMapper.weatherFor(emotion.emotionName, analysis.intensityScore);
        emotion.triggerScene = triggerScene(analysis);
        emotion.spectrum = toResultSpectrum(EmotionSpectrumDeriver.derive(analysis));
        return emotion;
    }

    private EmotionInsight toInsight(StructuredAiResults.Emotion emotion, AnalysisResult analysis, String source) {
        EmotionInsight insight = new EmotionInsight();
        insight.primaryEmotion = blank(emotion.emotionName) ? emotionName(analysis) : emotion.emotionName;
        double score = emotion.emotionScore == null ? analysis.intensityScore : emotion.emotionScore;
        insight.emotionScore = EmotionInsight.clampScore(score);
        insight.triggerScene = blank(emotion.triggerScene) ? triggerScene(analysis) : emotion.triggerScene;

        // Prefer an LLM-provided spectrum; otherwise derive deterministically.
        List<EmotionInsight.SpectrumEntry> spectrum = fromResultSpectrum(emotion.spectrum);
        if (spectrum.isEmpty()) {
            spectrum = EmotionSpectrumDeriver.derive(analysis);
        }
        insight.spectrum = spectrum;

        insight.weatherType = blank(emotion.weatherType)
                ? EmotionWeatherMapper.weatherFor(insight.primaryEmotion, insight.emotionScore)
                : emotion.weatherType;
        insight.analysisSource = source;
        return insight;
    }

    private List<StructuredAiResults.SpectrumEntry> toResultSpectrum(List<EmotionInsight.SpectrumEntry> entries) {
        List<StructuredAiResults.SpectrumEntry> out = new ArrayList<>();
        if (entries == null) return out;
        for (EmotionInsight.SpectrumEntry e : entries) {
            StructuredAiResults.SpectrumEntry r = new StructuredAiResults.SpectrumEntry();
            r.emotion = e.emotion;
            r.ratio = e.ratio;
            out.add(r);
        }
        return out;
    }

    private List<EmotionInsight.SpectrumEntry> fromResultSpectrum(List<StructuredAiResults.SpectrumEntry> entries) {
        List<EmotionInsight.SpectrumEntry> out = new ArrayList<>();
        if (entries == null) return out;
        for (StructuredAiResults.SpectrumEntry e : entries) {
            if (e == null || blank(e.emotion)) continue;
            out.add(new EmotionInsight.SpectrumEntry(e.emotion, e.ratio));
        }
        return out;
    }

    private String emotionName(AnalysisResult analysis) {
        String label = analysis.sentimentLabel == null ? "NEUTRAL" : analysis.sentimentLabel;
        List<String> themes = analysis.detectedThemes == null ? List.of() : analysis.detectedThemes;
        switch (label) {
            case "CRISIS":
                return "危机";
            case "NEGATIVE":
                if (themes.contains("情绪承压")) return "焦虑";
                if (themes.contains("关系牵动")) return "难过";
                if (themes.contains("自我评价")) return "自责";
                return "低落";
            case "POSITIVE":
                return "喜悦";
            default:
                return "平静";
        }
    }

    private String triggerScene(AnalysisResult analysis) {
        List<String> themes = analysis.detectedThemes == null ? List.of() : analysis.detectedThemes;
        if (!themes.isEmpty()) {
            return themes.get(0);
        }
        return "语义情绪分析";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
