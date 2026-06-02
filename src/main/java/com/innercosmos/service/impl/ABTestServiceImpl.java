package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.ABTestConfig;
import com.innercosmos.entity.ABTestMetrics;
import com.innercosmos.mapper.ABTestConfigMapper;
import com.innercosmos.mapper.ABTestMetricsMapper;
import com.innercosmos.service.ABTestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ABTestService with consistent hashing for group assignment.
 */
@Service
public class ABTestServiceImpl implements ABTestService {

    private final ABTestConfigMapper configMapper;
    private final ABTestMetricsMapper metricsMapper;

    // Cache for user group assignments
    private final Map<String, String> groupCache = new ConcurrentHashMap<>();

    public ABTestServiceImpl(ABTestConfigMapper configMapper, ABTestMetricsMapper metricsMapper) {
        this.configMapper = configMapper;
        this.metricsMapper = metricsMapper;
    }

    @Override
    public ABTestConfig getActiveConfig() {
        QueryWrapper<ABTestConfig> query = new QueryWrapper<>();
        query.eq("enabled", true).eq("status", "ACTIVE").orderByDesc("created_at").last("LIMIT 1");
        return configMapper.selectOne(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ABTestConfig saveConfig(ABTestConfig config) {
        config.enabled = config.enabled != null ? config.enabled : true;
        config.status = config.status != null ? config.status : "ACTIVE";
        config.startTime = config.startTime != null ? config.startTime : LocalDateTime.now();

        if (config.id != null) {
            configMapper.updateById(config);
        } else {
            configMapper.insert(config);
        }
        return config;
    }

    @Override
    public String assignGroup(Long userId, String moduleName) {
        ABTestConfig config = getActiveConfig();
        if (config == null || !config.enabled) {
            return "REMOTE"; // Default to REMOTE when no A/B test active
        }

        // Consistent hashing based on userId
        String cacheKey = userId + "_" + config.testName;
        String cachedGroup = groupCache.get(cacheKey);
        if (cachedGroup != null) {
            return cachedGroup;
        }

        // Hash-based assignment
        int hash = Math.abs(userId.hashCode());
        int threshold = (int) (Integer.MAX_VALUE * (config.mockPercentage / 100.0));
        String group = hash < threshold ? "MOCK" : "REMOTE";

        groupCache.put(cacheKey, group);

        // Record assignment
        ABTestMetrics metric = new ABTestMetrics();
        metric.userId = userId;
        metric.testName = config.testName;
        metric.assignedGroup = group;
        metric.moduleName = moduleName;
        metric.requestCount = 0;
        metric.avgLatency = 0.0;
        metric.successCount = 0;
        metric.fallbackCount = 0;
        metric.successRate = 0.0;
        metric.lastRequestAt = LocalDateTime.now();
        metricsMapper.insert(metric);

        config.totalParticipants = (config.totalParticipants != null ? config.totalParticipants : 0) + 1;
        configMapper.updateById(config);

        return group;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordMetrics(Long userId, String groupName, String moduleName,
                              double latency, boolean success, boolean fallback) {
        QueryWrapper<ABTestMetrics> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("last_request_at").last("LIMIT 1");
        ABTestMetrics metric = metricsMapper.selectOne(query);

        if (metric != null) {
            // Update existing metrics
            metric.requestCount = (metric.requestCount != null ? metric.requestCount : 0) + 1;
            metric.lastRequestAt = LocalDateTime.now();

            // Update average latency (exponential moving average)
            double oldLatency = metric.avgLatency != null ? metric.avgLatency : 0;
            double alpha = 0.2;
            metric.avgLatency = alpha * latency + (1 - alpha) * oldLatency;

            // Update success/fallback counts
            if (success) {
                metric.successCount = (metric.successCount != null ? metric.successCount : 0) + 1;
            }
            if (fallback) {
                metric.fallbackCount = (metric.fallbackCount != null ? metric.fallbackCount : 0) + 1;
            }

            // Calculate success rate
            int total = metric.requestCount;
            int successes = metric.successCount != null ? metric.successCount : 0;
            metric.successRate = total > 0 ? (double) successes / total : 0.0;

            metricsMapper.updateById(metric);
        }
    }

    @Override
    public Map<String, ABTestStats> getAggregatedStats(String testName) {
        QueryWrapper<ABTestMetrics> query = new QueryWrapper<>();
        query.eq("test_name", testName);
        List<ABTestMetrics> allMetrics = metricsMapper.selectList(query);

        Map<String, List<ABTestMetrics>> grouped = new HashMap<>();
        for (ABTestMetrics metric : allMetrics) {
            String group = metric.assignedGroup != null ? metric.assignedGroup : "UNKNOWN";
            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(metric);
        }

        Map<String, ABTestStats> result = new HashMap<>();
        for (Map.Entry<String, List<ABTestMetrics>> entry : grouped.entrySet()) {
            String groupName = entry.getKey();
            List<ABTestMetrics> metrics = entry.getValue();

            int totalRequests = metrics.stream().mapToInt(m -> m.requestCount != null ? m.requestCount : 0).sum();
            int successCount = metrics.stream().mapToInt(m -> m.successCount != null ? m.successCount : 0).sum();
            int fallbackCount = metrics.stream().mapToInt(m -> m.fallbackCount != null ? m.fallbackCount : 0).sum();
            double avgLatency = metrics.stream().mapToDouble(m -> m.avgLatency != null ? m.avgLatency : 0).average().orElse(0);
            double successRate = totalRequests > 0 ? (double) successCount / totalRequests : 0;

            result.put(groupName, new ABTestStats(groupName, totalRequests, successCount, fallbackCount, avgLatency, successRate));
        }

        return result;
    }

    @Override
    public String getUserGroup(Long userId, String testName) {
        QueryWrapper<ABTestMetrics> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("test_name", testName).orderByDesc("last_request_at").last("LIMIT 1");
        ABTestMetrics metric = metricsMapper.selectOne(query);
        return metric != null ? metric.assignedGroup : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleTest(Long configId, boolean enabled) {
        ABTestConfig config = configMapper.selectById(configId);
        if (config != null) {
            config.enabled = enabled;
            config.status = enabled ? "ACTIVE" : "PAUSED";
            configMapper.updateById(config);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ABTestReport completeTest(Long configId) {
        ABTestConfig config = configMapper.selectById(configId);
        if (config == null) {
            throw new IllegalArgumentException("Config not found");
        }

        config.status = "COMPLETED";
        config.enabled = false;
        config.endTime = LocalDateTime.now();
        configMapper.updateById(config);

        ABTestReport report = new ABTestReport();
        report.testName = config.testName;
        report.groupStats = getAggregatedStats(config.testName);
        report.generatedAt = LocalDateTime.now().toString();

        // Determine winner based on success rate and latency
        String winner = determineWinner(report.groupStats);
        report.winner = winner;
        report.recommendation = generateRecommendation(report.groupStats, winner);

        return report;
    }

    private String determineWinner(Map<String, ABTestStats> stats) {
        ABTestStats mock = stats.get("MOCK");
        ABTestStats remote = stats.get("REMOTE");

        if (mock == null || remote == null) {
            return stats.keySet().iterator().next();
        }

        // Compare success rates, with latency as tiebreaker
        if (Math.abs(mock.successRate - remote.successRate) < 0.05) {
            // Success rates are similar, use latency as tiebreaker
            return mock.avgLatency < remote.avgLatency ? "MOCK" : "REMOTE";
        }

        return mock.successRate > remote.successRate ? "MOCK" : "REMOTE";
    }

    private String generateRecommendation(Map<String, ABTestStats> stats, String winner) {
        ABTestStats winnerStats = stats.get(winner);
        if (winnerStats == null) {
            return "Insufficient data to make recommendation";
        }

        ABTestStats loserStats = stats.get(winner.equals("MOCK") ? "REMOTE" : "MOCK");

        StringBuilder rec = new StringBuilder();
        rec.append("Based on the A/B test results, ");
        rec.append(winner).append(" performed better ");
        rec.append("with ").append(String.format("%.1f%%", winnerStats.successRate * 100)).append(" success rate");
        rec.append(" and ").append(String.format("%.0fms", winnerStats.avgLatency)).append(" average latency.");

        if (loserStats != null) {
            double improvement = ((winnerStats.successRate - loserStats.successRate) / loserStats.successRate) * 100;
            rec.append(" This represents a ").append(String.format("%.1f%%", improvement)).append(" improvement");
            rec.append(" over the alternative.");
        }

        return rec.toString();
    }
}
