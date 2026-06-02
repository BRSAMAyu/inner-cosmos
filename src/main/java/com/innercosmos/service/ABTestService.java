package com.innercosmos.service;

import com.innercosmos.entity.ABTestConfig;
import com.innercosmos.entity.ABTestMetrics;
import java.util.List;
import java.util.Map;

/**
 * Service for managing A/B testing configuration and metrics.
 * Provides traffic splitting between MOCK and REMOTE LLM providers.
 */
public interface ABTestService {
    /**
     * Get active A/B test configuration.
     */
    ABTestConfig getActiveConfig();

    /**
     * Create or update A/B test configuration.
     */
    ABTestConfig saveConfig(ABTestConfig config);

    /**
     * Assign user to A/B test group based on configuration.
     * Returns "MOCK" or "REMOTE".
     */
    String assignGroup(Long userId, String moduleName);

    /**
     * Record metrics for an A/B test request.
     */
    void recordMetrics(Long userId, String groupName, String moduleName,
                       double latency, boolean success, boolean fallback);

    /**
     * Get aggregated metrics for a test.
     */
    Map<String, ABTestStats> getAggregatedStats(String testName);

    /**
     * Get user's assigned group for a specific test.
     */
    String getUserGroup(Long userId, String testName);

    /**
     * Pause or resume an A/B test.
     */
    void toggleTest(Long configId, boolean enabled);

    /**
     * Complete an A/B test and generate final report.
     */
    ABTestReport completeTest(Long configId);

    /**
     * Data class for aggregated statistics.
     */
    class ABTestStats {
        public String groupName;
        public int totalRequests;
        public int successCount;
        public int fallbackCount;
        public double avgLatency;
        public double successRate;

        public ABTestStats(String groupName, int totalRequests, int successCount,
                          int fallbackCount, double avgLatency, double successRate) {
            this.groupName = groupName;
            this.totalRequests = totalRequests;
            this.successCount = successCount;
            this.fallbackCount = fallbackCount;
            this.avgLatency = avgLatency;
            this.successRate = successRate;
        }
    }

    /**
     * Data class for A/B test report.
     */
    class ABTestReport {
        public String testName;
        public Map<String, ABTestStats> groupStats;
        public String winner;
        public String recommendation;
        public String generatedAt;
    }
}
