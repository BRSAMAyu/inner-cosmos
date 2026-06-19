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
import com.innercosmos.service.NotificationService;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final NotificationService notificationService;

    // IC-CAP-002 B-2: retry bookkeeping
    public static final int MAX_ATTEMPTS = 3;
    private static final int BACKOFF_BASE_MINUTES = 5;
    private static final int LAST_ERROR_MAX = 500;

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
                               AgentUserRelationshipService relationshipService,
                               NotificationService notificationService) {
        this.piiFilter = piiFilter;
        this.regenerator = regenerator;
        this.capsuleMapper = capsuleMapper;
        this.syncQueueMapper = syncQueueMapper;
        this.ltmMapper = ltmMapper;
        this.portraitService = portraitService;
        this.relationshipService = relationshipService;
        this.notificationService = notificationService;
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

        // Cache themes so an approved decide() can reuse them
        recentThemesCache.put(userId, recentThemes);

        // 5. Create or update queue entries (deduped) and async regenerate
        for (EchoCapsule capsule : capsules) {
            if (!"USER_CAPSULE".equals(capsule.capsuleType)) {
                continue; // Only sync user capsules, not seed capsules
            }
            String diff = buildDiffSummary(filtered, capsule);

            // IC-CAP-002 B-1: dedupe — if a PENDING row already exists for this
            // (user, capsule), update its diff + refresh createdAt instead of
            // inserting a duplicate (anti-storm under multiple trigger sources).
            CapsuleSyncQueue existing =
                    syncQueueMapper.findByUserCapsuleAndStatus(userId, capsule.id, "PENDING");
            if (existing != null) {
                existing.proposedContextDiff = diff;
                existing.createdAt = LocalDateTime.now();
                syncQueueMapper.updateById(existing);
            } else {
                CapsuleSyncQueue queue = new CapsuleSyncQueue();
                queue.userId = userId;
                queue.capsuleId = capsule.id;
                queue.status = "PENDING";
                queue.proposedContextDiff = diff;
                queue.createdAt = LocalDateTime.now();
                queue.attemptCount = 0;
                syncQueueMapper.insert(queue);
            }
        }

        log.info("Synced queue entries for user {}", userId);
    }

    /**
     * Returns pending sync queue entries for a user.
     *
     * @param userId The user ID
     * @return List of pending CapsuleSyncQueue entries
     */
    public List<CapsuleSyncQueue> pending(Long userId) {
        // IC-CAP-002 B-2: surface FAILED rows too, so users can see sync failures.
        return syncQueueMapper.findPendingOrFailed(userId);
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

            // IC-CAP-002 FIX-4: derive themes from the user's LTM (durable across restarts),
            // falling back to the in-memory cache only if LTM yields nothing.
            List<String> themes = resolveRecentThemes(userId);
            // IC-CAP-002 FIX-2: regenerate synchronously on the approval path. The previous
            // self-call to @Async asyncRegenerate() did NOT go through the Spring proxy
            // (same-bean self-invocation), so it ran on the request thread anyway — the
            // @Async was misleading. Call regenerateOne directly and acquire the rate-limit
            // permit here so approval-path regen is honestly synchronous.
            rateLimiter.acquire();
            regenerateOne(queue, filtered, themes);
            log.info("User {} approved sync for capsule {} (decision={})", userId, queue.capsuleId, decision);
        }

        return queue;
    }

    /**
     * Synchronous core used by the approval path ({@link #decide}) and by the retry job /
     * manual retry ({@link #retryFailed}). Re-runs regeneration for one queue row and applies
     * success/failure bookkeeping ON THE ROW so failures are visible (not swallowed):
     *  - success  -> status APPROVED→SYNCED + SYNC_DONE notification
     *  - failure  -> status FAILED + attemptCount++ + lastError + failedAt + nextRetryAt
     *                (exponential backoff) + SYNC_FAILED notification
     */
    public void regenerateOne(CapsuleSyncQueue queue,
                              PiiPrivacyFilter.FilteredPortrait filtered,
                              List<String> recentThemes) {
        try {
            regenerator.regenerate(queue.capsuleId, filtered, recentThemes);
            // success
            queue.status = "SYNCED";
            queue.lastError = null;
            // IC-CAP-002 FIX-5: clear stale failure metadata so a SYNCED row doesn't carry
            // leftover attemptCount/failedAt/nextRetryAt from earlier failed attempts.
            queue.attemptCount = 0;
            queue.failedAt = null;
            queue.nextRetryAt = null;
            queue.decidedAt = queue.decidedAt != null ? queue.decidedAt : LocalDateTime.now();
            syncQueueMapper.updateById(queue);
            notificationService.notify(queue.userId, "SYNC_DONE",
                    "共鸣体已同步", "你授权的画像更新已同步到共鸣体。",
                    queue.id, "CAPSULE_SYNC");
            log.info("Capsule {} sync SYNCED (queue {})", queue.capsuleId, queue.id);
        } catch (Exception e) {
            int attempt = (queue.attemptCount == null ? 0 : queue.attemptCount) + 1;
            queue.status = "FAILED";
            queue.attemptCount = attempt;
            queue.lastError = truncate(e.getMessage(), LAST_ERROR_MAX);
            queue.failedAt = LocalDateTime.now();
            queue.nextRetryAt = LocalDateTime.now().plusMinutes(backoffMinutes(attempt));
            syncQueueMapper.updateById(queue);
            notificationService.notify(queue.userId, "SYNC_FAILED",
                    "共鸣体同步失败", "同步暂时失败，系统会自动重试。",
                    queue.id, "CAPSULE_SYNC");
            log.error("Capsule {} sync FAILED (queue {}, attempt {}): {}",
                    queue.capsuleId, queue.id, attempt, e.getMessage());
        }
    }

    /**
     * IC-CAP-002 B-2: re-run regeneration for a single FAILED queue row (used by the
     * retry job and the manual /retry endpoint). Rebuilds the filtered portrait from
     * the user's current data. No-op if the row has exhausted its attempt budget.
     */
    public void retryFailed(Long queueId) {
        CapsuleSyncQueue queue = syncQueueMapper.selectById(queueId);
        if (queue == null) return;
        if (queue.attemptCount != null && queue.attemptCount >= MAX_ATTEMPTS) {
            log.info("Queue {} reached MAX_ATTEMPTS ({}); not retrying", queueId, MAX_ATTEMPTS);
            return;
        }
        PiiPrivacyFilter.PortraitSnapshot snapshot = buildSnapshot(queue.userId);
        PiiPrivacyFilter.FilteredPortrait filtered = piiFilter.filter(snapshot, Map.of());
        // IC-CAP-002 FIX-4: rebuild themes from the user's LTM rather than depending on the
        // in-memory cache, which is empty after a JVM restart or when the retry fires in a
        // different process than the original trigger. Falls back to the cache only if LTM
        // yields no themes.
        List<String> themes = resolveRecentThemes(queue.userId);
        rateLimiter.acquire();
        regenerateOne(queue, filtered, themes);
    }

    /**
     * IC-CAP-002 FIX-4: durable theme resolution. Queries the user's approved LTM (the same
     * source {@link #buildSnapshot} uses) and derives themes via {@link #buildRecentThemes}.
     * Falls back to the in-memory {@link #recentThemesCache} only when LTM produces nothing,
     * so the retry/approval paths no longer silently degrade after a restart.
     */
    private List<String> resolveRecentThemes(Long userId) {
        List<UserLongTermMemory> ltm = ltmMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserLongTermMemory>()
                        .eq("user_id", userId)
                        .eq("user_approved", true)
                        .orderByDesc("created_at")
                        .last("LIMIT 50")
        );
        List<String> themes = buildRecentThemes(ltm);
        if (themes.isEmpty()) {
            return recentThemesCache.getOrDefault(userId, List.of());
        }
        return themes;
    }

    private long backoffMinutes(int attempt) {
        // exponential: base * 2^(attempt-1), bounded
        int exp = Math.max(0, attempt - 1);
        long minutes = (long) BACKOFF_BASE_MINUTES << Math.min(exp, 6);
        return Math.min(minutes, 60L);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
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