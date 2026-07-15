package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.vo.MemoryEvidencePackVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Explainable online reranker. PostgreSQL remains the authority; this checkpoint uses
 * structured filters + exact lexical/entity matches + local character-ngram semantic
 * similarity. Provider embeddings/pgvector become an additional candidate source, not
 * a replacement for these privacy, contradiction and budget gates.
 */
@Service
public class MemoryRetrievalServiceImpl implements MemoryRetrievalService {
    private static final Set<String> CURRENT = Set.of("ACTIVE");
    private final MemoryCardMapper memoryMapper;

    public MemoryRetrievalServiceImpl(MemoryCardMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public MemoryEvidencePackVO retrieve(Long userId, MemoryRetrievalQuery raw) {
        String text = raw == null || raw.query() == null ? "" : raw.query().trim();
        String task = raw == null || raw.task() == null ? "AURORA_CONVERSATION" : raw.task().trim().toUpperCase(Locale.ROOT);
        int max = clamp(raw == null ? null : raw.maxResults(), 1, 20, 6);
        int budget = clamp(raw == null ? null : raw.tokenBudget(), 64, 4000, 800);
        Set<String> layers = raw == null || raw.allowedLayers() == null ? Set.of()
                : raw.allowedLayers().stream().map(v -> v.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        boolean includeContradicted = raw != null && raw.includeContradicted();

        QueryWrapper<MemoryCard> db = new QueryWrapper<MemoryCard>().eq("user_id", userId)
                .ne("status", "FORGOTTEN").ne("status", "SUPERSEDED").ne("status", "ARCHIVED");
        if (!includeContradicted) db.in("status", CURRENT);
        if (!layers.isEmpty()) db.in("memory_layer", layers);
        List<Scored> scored = memoryMapper.selectList(db).stream()
                .map(card -> score(card, text, task)).filter(row -> text.isBlank() || row.score > 0.08)
                .sorted(Comparator.comparingDouble(Scored::score).reversed()).toList();

        List<MemoryEvidencePackVO.Evidence> selected = new ArrayList<>();
        Map<String, Integer> layerCounts = new HashMap<>();
        int used = 0;
        for (Scored row : scored) {
            if (selected.size() >= max) break;
            String layer = row.card.memoryLayer == null ? "EPISODIC" : row.card.memoryLayer;
            if (layerCounts.getOrDefault(layer, 0) >= Math.max(2, max / 2)) continue;
            int cost = estimate(row.card);
            if (!selected.isEmpty() && used + cost > budget) continue;
            used += cost; layerCounts.merge(layer, 1, Integer::sum);
            selected.add(new MemoryEvidencePackVO.Evidence(row.card.id, row.card.title, row.card.summary,
                    layer, round(row.score), row.contributions, row.card.versionNo, row.card.provenanceRefs));
        }
        return new MemoryEvidencePackVO(task, text, budget, used, selected,
                includeContradicted ? List.of("FORGOTTEN", "SUPERSEDED", "ARCHIVED")
                        : List.of("FORGOTTEN", "SUPERSEDED", "ARCHIVED", "CONTRADICTED"));
    }

    private Scored score(MemoryCard card, String query, String task) {
        String document = String.join(" ", safe(card.title), safe(card.summary), safe(card.keywordTags), safe(card.peopleTags));
        double lexical = lexical(query, document);
        double semantic = cosine(ngrams(query), ngrams(document));
        double taskFit = taskFit(task, card);
        double freshness = freshness(card.lastTouchedAt == null ? card.createdAt : card.lastTouchedAt);
        double salience = Math.min(1, value(card.emotionalGravity) / 3.0);
        double authority = Math.min(1, value(card.confidence));
        double staleness = freshness < 0.2 ? 0.12 : 0;
        double score = lexical * 0.32 + semantic * 0.25 + taskFit * 0.18
                + freshness * 0.10 + salience * 0.08 + authority * 0.07 - staleness;
        List<String> why = new ArrayList<>();
        if (lexical > 0) why.add("精确词语/人物匹配 " + round(lexical));
        if (semantic > 0.1) why.add("语义近似 " + round(semantic));
        if (taskFit > 0.4) why.add("适合当前任务 " + round(taskFit));
        if (freshness > 0.6) why.add("近期仍活跃");
        if (authority > 0.8) why.add("高置信或用户确认");
        return new Scored(card, score, why);
    }

    private static double taskFit(String task, MemoryCard card) {
        String layer = safe(card.memoryLayer).toUpperCase(Locale.ROOT);
        String type = safe(card.memoryType).toUpperCase(Locale.ROOT);
        if (task.contains("ACTION")) return type.equals("TODO") || layer.equals("PROSPECTIVE") ? 1 : 0.25;
        if (task.contains("RELATION") || task.contains("CAPSULE")) return type.equals("RELATION") || layer.equals("RELATIONAL") ? 1 : 0.2;
        if (task.contains("EMOTION")) return layer.equals("EMOTIONAL") || type.equals("EMOTION") ? 1 : 0.3;
        if (task.contains("PROFILE")) return layer.equals("SEMANTIC") || layer.equals("PROCEDURAL") ? 1 : 0.35;
        return layer.equals("EPISODIC") || layer.equals("SEMANTIC") || layer.equals("EMOTIONAL") ? 0.75 : 0.45;
    }

    private static double lexical(String query, String document) {
        Set<String> q = terms(query); if (q.isEmpty()) return 0;
        Set<String> d = terms(document); long hit = q.stream().filter(d::contains).count();
        return (double) hit / q.size();
    }
    private static Set<String> terms(String value) {
        Set<String> result = new HashSet<>();
        for (String token : safe(value).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (token.length() > 1) result.add(token);
        result.addAll(ngrams(value).keySet()); return result;
    }
    private static Map<String, Double> ngrams(String value) {
        String compact = safe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        Map<String, Double> result = new LinkedHashMap<>();
        int n = compact.codePointCount(0, compact.length()) < 3 ? 1 : 2;
        int[] cps = compact.codePoints().toArray();
        for (int i = 0; i <= cps.length - n; i++) {
            String key = new String(cps, i, n); result.merge(key, 1.0, Double::sum);
        }
        return result;
    }
    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        double dot = 0, aa = 0, bb = 0;
        for (double v : a.values()) aa += v * v;
        for (double v : b.values()) bb += v * v;
        for (Map.Entry<String, Double> entry : a.entrySet()) dot += entry.getValue() * b.getOrDefault(entry.getKey(), 0.0);
        return dot / Math.sqrt(aa * bb);
    }
    private static double freshness(LocalDateTime time) {
        if (time == null) return 0.5;
        long days = Math.max(0, Duration.between(time, LocalDateTime.now()).toDays());
        return Math.exp(-days / 45.0);
    }
    private static int estimate(MemoryCard card) { return Math.max(8, (safe(card.title).length() + safe(card.summary).length()) / 3); }
    private static int clamp(Integer value, int min, int max, int fallback) { return value == null ? fallback : Math.max(min, Math.min(max, value)); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static double value(Double value) { return value == null ? 0.5 : value; }
    private static double round(double value) { return Math.round(value * 1000.0) / 1000.0; }
    private record Scored(MemoryCard card, double score, List<String> contributions) {}
}
