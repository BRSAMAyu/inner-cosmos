package com.innercosmos.service;

import com.innercosmos.entity.RelationMention;
import java.util.List;
import java.util.Map;

/**
 * Service for analyzing and visualizing relationship networks.
 * Tracks emotional patterns in user relationships over time.
 */
public interface RelationNetworkService {
    /**
     * Extract relationship mentions from a memory card.
     */
    void extractFromMemory(Long userId, Long memoryCardId);

    /**
     * Find all relationships for a user.
     */
    List<RelationMention> findRelations(Long userId);

    /**
     * Get relationship statistics by type.
     */
    Map<String, Integer> getRelationStats(Long userId);

    /**
     * Find high-emotion relationships (may need attention).
     */
    List<RelationMention> findHighEmotionRelations(Long userId);

    /**
     * Get relationship timeline for a specific person.
     */
    List<RelationMention.TimelinePoint> getRelationTimeline(Long userId, String relationLabel);

    /**
     * CP-34: 关系互动回顾 — counts what actually happened for one relation inside the
     * trailing window (mentions, distinct active weeks, emotion spectrum, recent
     * triggers). Replaces the old evaluative health/temperature score: this is a record,
     * not a verdict.
     */
    com.innercosmos.vo.RelationInteractionReviewVO interactionReview(Long userId, String relationLabel, int weeks);
}
