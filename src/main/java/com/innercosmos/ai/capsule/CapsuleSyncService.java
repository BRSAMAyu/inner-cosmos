package com.innercosmos.ai.capsule;

import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.CapsuleSyncQueue;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.UserLongTermMemory;
import com.innercosmos.mapper.CapsuleSyncQueueMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.UserLongTermMemoryMapper;
import com.innercosmos.service.MemoryService;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capsule Sync Service manages the privacy-preserving synchronization
 * between user portrait data and Echo Capsule personas.
 *
 * When portrait or relationship data changes, this service:
 * 1. Applies PII filter to pseudonymize sensitive data
 * 2. Queues proposed context updates for user approval
 * 3. Asynchronously regenerates capsule context after approval
 */
@Service
public class CapsuleSyncService {
    private static final Logger log = LoggerFactory.getLogger(CapsuleSyncService.class);

    private final PiiPrivacyFilter piiFilter;
    private final CapsuleContextRegenerator regenerator;
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleSyncQueueMapper syncQueueMapper;
    private final UserLongTermMemoryMapper ltmMapper;
    private final UserPortraitService portraitService;
    private final AgentUserRelationshipService relationshipService;

    // Rate limiter to prevent LLM spam during bulk updates
    private final RateLimiter rateLimiter = RateLimiter.create(5.0); // 5 capsule updates per second max

    // In-memory cache of recent themes per user
    private final Map<Long, List<String>> recentThemesCache = new ConcurrentHashMap<>();

    public CapsuleSyncService(PiiPrivacyFilter piiFilter,
                               CapsuleContextRegenerator regenerator,
                               EchoCapsuleMapper capsuleMapper,
                               CapsuleSyncQueueMapper syncQueueMapper,
                               UserLongTermMemoryMapper ltmMapper,
                               UserPortraitService portraitService,
                               AgentUserRelationshipService relationshipService) {
        this.piiFilter = piiFilter;
        this.regenerator = regenerator;
        this.capsuleMapper = capsuleMapper;
        this.syncQueueMapper = syncQueueMapper;
        this.ltmMapper = ltmMapper;
        this.portraitService = portraitService;
        this.relationshipService = relationshipService;
    }

    /**
     * Main entry point: called from M3 SessionCloser when portrait or relationship changes.
     * Creates sync queue entries for all user capsules and triggers async regeneration.
     *
     * @param userId The user whose portrait/relationship changed
     */
    public void onPortraitOrRelationshipChanged(Long userId) {
        log.info("Portrait/relationship changed for user {}, initiating capsule sync", userId);

        // 1. Gather portrait snapshot, LTM, and relationship
        List<UserLongTermMemory> ltmEntries = ltmMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserLongTermMemory>()
                        .eq("user_id", userId)
                        .eq("user_approved", true)
                        .orderByDesc("created_at")
                        .last("LIMIT 50")
        );
        PiiPrivacyFilter.PortraitSnapshot snapshot = piiFilter.createSnapshot(userId, ltmEntries);

        // 2. Apply PII filter
        PiiPrivacyFilter.FilteredPortrait filtered = piiFilter.filter(snapshot, Map.of());

        // 3. Get user's capsules
        List<EchoCapsule> capsules = capsuleMapper.findByOwner(userId);
        if (capsules.isEmpty()) {
            log.info("User {} has no capsules to sync", userId);
            return;
        }

        // 4. Build recent themes from LTM
        List<String> recentThemes = buildRecentThemes(ltmEntries);

        // 5. Create queue entries and async regenerate
        for (EchoCapsule capsule : capsules) {
            if (!"USER_CAPSULE".equals(capsule.capsuleType)) {
                continue; // Only sync user capsules, not seed capsules
            }
            CapsuleSyncQueue queue = new CapsuleSyncQueue();
            queue.userId = userId;
            queue.capsuleId = capsule.id;
            queue.status = "PENDING";
            queue.proposedContextDiff = buildDiffSummary(filtered, capsule);
            queue.createdAt = LocalDateTime.now();
            syncQueueMapper.insert(queue);

            // Async regeneration
            asyncRegenerate(capsule.id, filtered, recentThemes);
        }

        log.info("Created {} sync queue entries for user {}", capsules.size(), userId);
    }

    /**
     * Returns pending sync queue entries for a user.
     *
     * @param userId The user ID
     * @return List of pending CapsuleSyncQueue entries
     */
    public List<CapsuleSyncQueue> pending(Long userId) {
        return syncQueueMapper.findByUserAndStatus(userId, "PENDING");
    }

    /**
     * Handle user decision on a sync queue entry.
     *
     * @param userId       The user making the decision
     * @param queueId      The queue entry ID
     * @param decision     ALLOW, ALLOW_PARTIAL, or REJECT
     * @param allowedFields Fields the user explicitly allowed (for ALLOW_PARTIAL)
     * @return The updated queue entry
     */
    @Transactional
    public CapsuleSyncQueue decide(Long userId, Long queueId, String decision, List<String> allowedFields) {
        CapsuleSyncQueue queue = syncQueueMapper.selectById(queueId);
        if (queue == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "同步队列记录不存在");
        }
        if (!userId.equals(queue.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此记录");
        }
        if (!"PENDING".equals(queue.status)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST, "该记录已处理");
        }

        queue.decidedAt = LocalDateTime.now();

        if ("REJECT".equals(decision)) {
            queue.status = "REJECTED";
            syncQueueMapper.updateById(queue);
            log.info("User {} rejected sync for capsule {}", userId, queue.capsuleId);
        } else {
            // ALLOW or ALLOW_PARTIAL: apply the update
            queue.status = "APPROVED";
            syncQueueMapper.updateById(queue);

            // Re-filter with user's field overrides if PARTIAL
            PiiPrivacyFilter.PortraitSnapshot snapshot = buildSnapshot(userId);
            PiiPrivacyFilter.FilteredPortrait filtered;
            if ("ALLOW_PARTIAL".equals(decision) && allowedFields != null && !allowedFields.isEmpty()) {
                Map<String, String> overrides = buildOverridesFromAllowed(allowedFields);
                filtered = piiFilter.filter(snapshot, overrides);
            } else {
                filtered = piiFilter.filter(snapshot, Map.of());
            }

            List<String> themes = recentThemesCache.getOrDefault(userId, List.of());
            asyncRegenerate(queue.capsuleId, filtered, themes);
            log.info("User {} approved sync for capsule {} (decision={})", userId, queue.capsuleId, decision);
        }

        return queue;
    }

    @Async
    public void asyncRegenerate(Long capsuleId,
                                PiiPrivacyFilter.FilteredPortrait filtered,
                                List<String> recentThemes) {
        rateLimiter.acquire();
        try {
            regenerator.regenerate(capsuleId, filtered, recentThemes);
        } catch (Exception e) {
            log.error("Async regeneration failed for capsule {}: {}", capsuleId, e.getMessage());
        }
    }

    private List<String> buildRecentThemes(List<UserLongTermMemory> ltmEntries) {
        List<String> themes = new ArrayList<>();
        for (UserLongTermMemory m : ltmEntries) {
            if ("THEME".equals(m.factType) || "TOPIC".equals(m.factType)) {
                themes.add(m.factValue);
            }
        }
        return themes.stream().limit(10).toList();
    }

    private String buildDiffSummary(PiiPrivacyFilter.FilteredPortrait filtered, EchoCapsule capsule) {
        return String.format(
                "{\"pseudonym\":\"%s\",\"ageRange\":\"%s\",\"occupation\":\"%s\",\"city\":\"%s\",\"values\":[\"%s\"],\"dropped\":%s}",
                filtered.pseudonym() != null ? filtered.pseudonym() : "",
                filtered.ageRange() != null ? filtered.ageRange() : "",
                filtered.occupationCategory() != null ? filtered.occupationCategory() : "",
                filtered.city() != null ? filtered.city() : "",
                String.join("\",\"", filtered.values()),
                filtered.droppedFields()
        );
    }

    private PiiPrivacyFilter.PortraitSnapshot buildSnapshot(Long userId) {
        List<UserLongTermMemory> ltm = ltmMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserLongTermMemory>()
                        .eq("user_id", userId)
                        .eq("user_approved", true)
                        .orderByDesc("created_at")
                        .last("LIMIT 50")
        );
        return piiFilter.createSnapshot(userId, ltm);
    }

    private Map<String, String> buildOverridesFromAllowed(List<String> allowedFields) {
        Map<String, String> overrides = new java.util.HashMap<>();
        // By default, drop everything; re-allow only specified fields
        for (String field : allowedFields) {
            overrides.put(field, "ALLOW");
        }
        // Always pseudonymize name
        overrides.put("real_name", "PSEUDONYMIZE");
        return overrides;
    }
}