package com.innercosmos.service;

import com.innercosmos.entity.BeliefPattern;
import java.util.List;

/**
 * Service for extracting and managing belief patterns from user memories.
 * Uses LLM-based semantic analysis to identify recurring cognitive patterns.
 */
public interface BeliefExtractService {
    /**
     * Extract belief patterns from a memory card.
     * Creates or updates belief patterns based on semantic analysis.
     */
    void extractFromMemory(Long userId, Long memoryCardId);

    /**
     * Find all active belief patterns for a user.
     */
    List<BeliefPattern> findBeliefs(Long userId);

    /**
     * Find beliefs by category.
     */
    List<BeliefPattern> findByCategory(Long userId, String category);

    /**
     * Find strong beliefs (strengthScore > threshold).
     */
    List<BeliefPattern> findStrongBeliefs(Long userId, double minStrength);

    /**
     * Find contradicting beliefs that may cause cognitive dissonance.
     */
    List<ContradictionPair> findContradictions(Long userId);

    /**
     * Recalculate belief strength based on all supporting memories.
     *
     * <p>CP-21 optimistic concurrency: {@code expectedVersion} is the caller's pin on
     * the belief row it rendered. {@code null} means legacy/unconditional intent. A
     * stale pin (or a racing writer between the read and the write) is rejected as
     * {@link com.innercosmos.common.ErrorCode#CONFLICT} with no data touched; a passing
     * pin (or no pin) recalculates and atomically bumps {@code version} by one.</p>
     */
    void recalculateStrength(Long userId, Long beliefId, Integer expectedVersion);

    /**
     * Data class for contradiction pairs.
     */
    class ContradictionPair {
        public BeliefPattern beliefA;
        public BeliefPattern beliefB;
        public String contradictionReason;
    }
}
