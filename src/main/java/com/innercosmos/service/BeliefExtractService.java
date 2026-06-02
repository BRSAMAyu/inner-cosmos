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
    List<BeliefPattern.ContradictionPair> findContradictions(Long userId);

    /**
     * Recalculate belief strength based on all supporting memories.
     */
    void recalculateStrength(Long beliefId);

    /**
     * Data class for contradiction pairs.
     */
    class ContradictionPair {
        public BeliefPattern beliefA;
        public BeliefPattern beliefB;
        public String contradictionReason;
    }
}
