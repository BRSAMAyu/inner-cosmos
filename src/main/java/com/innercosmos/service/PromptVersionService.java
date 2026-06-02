package com.innercosmos.service;

import com.innercosmos.entity.PromptTemplateEntity;
import java.util.List;
import java.util.Map;

public interface PromptVersionService {

    String getActivePrompt(String promptKey);

    PromptTemplateEntity createPrompt(String promptKey, String content, String description);

    List<PromptTemplateEntity> listVersions(String promptKey);

    /**
     * Rollback to a specific version.
     */
    PromptTemplateEntity rollbackToVersion(String promptKey, int version);

    /**
     * Enable/disable a specific version.
     */
    void toggleVersion(Long promptId, boolean enabled);

    /**
     * Get A/B test configuration for a prompt key.
     */
    PromptTemplateEntity getPromptVariant(String promptKey, String variant);

    /**
     * Record prompt performance metrics.
     */
    void recordMetrics(String promptKey, int version, double successRate, double avgLatency);

    /**
     * Get performance comparison across versions.
     */
    Map<Integer, PromptMetrics> getPerformanceMetrics(String promptKey);

    /**
     * Find prompts requiring review (low performance).
     */
    List<PromptTemplateEntity> findLowPerformingPrompts(double threshold);

    /**
     * Data class for prompt metrics.
     */
    class PromptMetrics {
        public int version;
        public double successRate;
        public double avgLatency;
        public int usageCount;

        public PromptMetrics(int version, double successRate, double avgLatency, int usageCount) {
            this.version = version;
            this.successRate = successRate;
            this.avgLatency = avgLatency;
            this.usageCount = usageCount;
        }
    }
}
