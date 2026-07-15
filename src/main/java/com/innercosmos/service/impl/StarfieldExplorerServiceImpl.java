package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.MemoryLink;
import com.innercosmos.mapper.MemoryLinkMapper;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.StarfieldExplorerService;
import com.innercosmos.vo.StarfieldSceneVO;
import com.innercosmos.vo.StarfieldVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StarfieldExplorerServiceImpl implements StarfieldExplorerService {
    private final MemoryService memoryService;
    private final MemoryLinkMapper linkMapper;

    public StarfieldExplorerServiceImpl(MemoryService memoryService, MemoryLinkMapper linkMapper) {
        this.memoryService = memoryService;
        this.linkMapper = linkMapper;
    }

    @Override
    public StarfieldSceneVO explore(Long userId, String rawMode, String query, String layer, String person) {
        String mode = normalizeMode(rawMode);
        String needle = normalize(query);
        String layerFilter = normalize(layer).toUpperCase(Locale.ROOT);
        String personFilter = normalize(person);
        List<StarfieldVO> stars = memoryService.starfield(userId).stream()
                .filter(star -> needle.isEmpty() || contains(star.title, needle) || contains(star.summary, needle))
                .filter(star -> layerFilter.isEmpty() || layerFilter.equalsIgnoreCase(star.memoryLayer))
                .filter(star -> personFilter.isEmpty() || contains(star.peopleTags, personFilter))
                .collect(Collectors.toList());
        Map<Long, List<Long>> links = links(userId);
        for (int i = 0; i < stars.size(); i++) {
            StarfieldVO star = stars.get(i);
            star.connectedMemoryIds = links.getOrDefault(star.id, List.of());
            project(star, i, stars.size(), mode);
        }
        List<StarfieldVO> accessible = stars.stream()
                .sorted(Comparator.comparing((StarfieldVO star) -> star.occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(star -> star.title)).toList();
        return new StarfieldSceneVO(mode, explanation(mode), stars, accessible, legend(mode), LocalDateTime.now());
    }

    private Map<Long, List<Long>> links(Long userId) {
        List<MemoryLink> rows = linkMapper.selectList(new QueryWrapper<MemoryLink>()
                .eq("user_id", userId).eq("status", "ACTIVE"));
        Map<Long, List<Long>> result = new java.util.HashMap<>();
        for (MemoryLink row : rows) {
            result.computeIfAbsent(row.sourceMemoryId, ignored -> new java.util.ArrayList<>()).add(row.targetMemoryId);
            result.computeIfAbsent(row.targetMemoryId, ignored -> new java.util.ArrayList<>()).add(row.sourceMemoryId);
        }
        return result;
    }

    private void project(StarfieldVO star, int index, int total, String mode) {
        if ("TIME".equals(mode)) {
            long days = star.occurredAt == null ? 90 : Math.max(0, Duration.between(star.occurredAt, LocalDateTime.now()).toDays());
            star.x = 90 - Math.min(180, days) * 0.9;
            star.y = layerBand(star.memoryLayer) + ((index % 3) - 1) * 7;
        } else if ("PEOPLE".equals(mode)) {
            int hash = safe(star.peopleTags).hashCode();
            double angle = Math.floorMod(hash, 360) * Math.PI / 180.0;
            double radius = 35 + Math.floorMod(hash / 17, 45);
            star.x = Math.cos(angle) * radius; star.y = Math.sin(angle) * radius;
        } else {
            int hash = safe(star.theme).hashCode();
            double centerAngle = Math.floorMod(hash, 360) * Math.PI / 180.0;
            double centerRadius = 48;
            double offset = (index - total / 2.0) * 0.18;
            star.x = Math.cos(centerAngle + offset) * centerRadius;
            star.y = Math.sin(centerAngle + offset) * centerRadius;
        }
    }

    private static double layerBand(String layer) {
        return switch (safe(layer).toUpperCase(Locale.ROOT)) {
            case "EMOTIONAL" -> -55; case "RELATIONAL" -> -25; case "SEMANTIC" -> 5;
            case "PROCEDURAL" -> 35; case "PROSPECTIVE" -> 60; default -> 15;
        };
    }
    private static String normalizeMode(String value) {
        String mode = normalize(value).toUpperCase(Locale.ROOT);
        return List.of("TIME", "THEME", "PEOPLE").contains(mode) ? mode : "TIME";
    }
    private static String explanation(String mode) { return switch (mode) {
        case "THEME" -> "相近主题聚成星座；距离表达主题归属，不代表好坏。";
        case "PEOPLE" -> "人物标签形成共同轨道；没有人物信息的记忆停留在外围。";
        default -> "从右侧的现在向左回望过去；纵向位置区分记忆层。";
    }; }
    private static Map<String, String> legend(String mode) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("尺寸", "情感重力与长期重要性"); result.put("亮度", "近期活跃程度");
        result.put("边缘", "理解置信度"); result.put("连线", "合并、拆分、矛盾或替代关系");
        result.put("距离", explanation(mode)); return result;
    }
    private static boolean contains(String value, String needle) { return safe(value).toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT)); }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
    private static String safe(String value) { return value == null ? "" : value; }
}
