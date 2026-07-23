package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.GravityTimePolicy;
import com.innercosmos.service.MemorySettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class NightlyMemorySettlementJob {
    private static final Logger log = LoggerFactory.getLogger(NightlyMemorySettlementJob.class);
    private final UserMapper userMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final GravityService gravityService;
    private final GravityTimePolicy gravityTimePolicy;
    private final MemorySettlementService settlementService;
    private final EmotionBaselineService emotionBaselineService;
    private final EchoCapsuleMapper echoCapsuleMapper;

    // IC-DATA-001: paginate user iteration to avoid full-table OOM on large datasets.
    private static final int BATCH_SIZE = 200;

    /**
     * Package-private override for tests — allows forcing a smaller page size
     * (e.g., 2) so that multi-batch iteration can be exercised without 200+ rows.
     * Production code never touches this field; it remains equal to BATCH_SIZE.
     */
    int batchSizeForTest = BATCH_SIZE;

    // IC-CAP-002 B-4: nightly multiplicative decay toward floors.
    private static final double ENERGY_DECAY = 0.97;
    private static final double ENERGY_FLOOR = 0.3;
    private static final double FRESHNESS_DECAY = 0.95;
    private static final double FRESHNESS_FLOOR = 0.0;

    public NightlyMemorySettlementJob(UserMapper userMapper,
                                      MemoryCardMapper memoryCardMapper,
                                      GravityService gravityService,
                                      GravityTimePolicy gravityTimePolicy,
                                      MemorySettlementService settlementService,
                                      EmotionBaselineService emotionBaselineService,
                                      EchoCapsuleMapper echoCapsuleMapper) {
        this.userMapper = userMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.gravityService = gravityService;
        this.gravityTimePolicy = gravityTimePolicy;
        this.settlementService = settlementService;
        this.emotionBaselineService = emotionBaselineService;
        this.echoCapsuleMapper = echoCapsuleMapper;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "nightly-memory-settlement", lockAtMostFor = "PT26H", lockAtLeastFor = "PT23H")
    public void nightlyRecalculation() {
        log.info("Nightly memory settlement started");
        long offset = 0;
        List<User> batch;
        int batchNum = 0;
        int processed = 0;
        int failed = 0;
        do {
            Page<User> page = new Page<>(offset / batchSizeForTest + 1, batchSizeForTest);
            batch = userMapper.selectPage(page, null).getRecords();
            batchNum++;
            log.info("NightlySettlement: processing batch {}, offset {}, size {}", batchNum, offset, batch.size());
            for (User user : batch) {
                try {
                    recalculateGravity(user.id);
                    settlementService.updateThemeAggregation(user.id);
                    // IC-EMO-003: recompute the N-day emotion baseline and bridge it
                    // (buffered) into the portrait. This is the cadence at which the
                    // mid-term baseline — never an individual real-time trace — is
                    // allowed to move the emotion portrait dims (anti-thrash, Spec §2).
                    emotionBaselineService.bridgeToPortrait(user.id);
                    // IC-CAP-002 B-4: decay this user's capsule energy/freshness toward floors.
                    decayEnergyForUser(user.id);
                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.error("Nightly settlement failed for user {}: {}", user.id, e.getMessage(), e);
                }
            }
            offset += batch.size();
        } while (batch.size() == batchSizeForTest);
        log.info("Nightly memory settlement completed for {} users ({} failed)", processed, failed);
    }

    /**
     * IC-CAP-002 B-4: multiplicative nightly decay of a user's capsules toward floors.
     * echoEnergy = max(0.3, energy * 0.97); freshnessScore = max(0.0, freshness * 0.95).
     * Deterministic — activity bumps (in PersonaChatServiceImpl) counter this.
     */
    void decayEnergyForUser(Long userId) {
        // IC-CAP-002 FIX-3: Spec §4 B-4 scopes nightly decay to the user's PUBLIC capsules
        // (is_public = TRUE AND visibility_status = 'PUBLIC'), not all owned capsules.
        List<EchoCapsule> capsules = echoCapsuleMapper.findPublicByOwner(userId);
        for (EchoCapsule c : capsules) {
            double energy = c.echoEnergy == null ? ENERGY_FLOOR : c.echoEnergy;
            double freshness = c.freshnessScore == null ? 0.0 : c.freshnessScore;
            c.echoEnergy = Math.max(ENERGY_FLOOR, energy * ENERGY_DECAY);
            c.freshnessScore = Math.max(FRESHNESS_FLOOR, freshness * FRESHNESS_DECAY);
            echoCapsuleMapper.updateById(c);
        }
    }

    private void recalculateGravity(Long userId) {
        QueryWrapper<MemoryCard> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("status", "ACTIVE");
        List<MemoryCard> cards = memoryCardMapper.selectList(query);
        for (MemoryCard card : cards) {
            // M-014: apply real time-decay — gravity fades with days since the card was last
            // touched (falling back to createdAt when never re-touched), so the starfield ages
            // instead of freezing at creation-time ordering. Card-creation paths still pass 0.
            // Regression (Gemini audit 1.5): shared GravityTimePolicy instead of this method's
            // own inline anchor logic -- see GravityRecalculationServiceImpl, which now uses the same
            // policy instead of its previously-divergent hardcoded 30-day fallback.
            long daysSince = gravityTimePolicy.daysSinceAnchor(card);
            double gravity = gravityService.calculateGravity(
                    card.intensityScore, card.recurrenceCount,
                    card.userImportance, card.triggerCount, daysSince);
            // Regression (Gemini audit 2.1, P0): field-level conditional update guarded on
            // versionNo instead of a whole-entity updateById() -- see GravityRecalculationServiceImpl
            // for the identical pattern and rationale (background recompute must never overwrite
            // a concurrent user edit; 0 rows means skip, the next nightly run retries).
            int updated = memoryCardMapper.update(null, new UpdateWrapper<MemoryCard>()
                    .eq("id", card.id).eq("version_no", card.versionNo)
                    .set("emotional_gravity", gravity)
                    .set("version_no", (card.versionNo == null ? 0 : card.versionNo) + 1));
            if (updated == 0) {
                log.info("Memory card {} changed concurrently during nightly gravity recompute; skipping, next run will retry", card.id);
            }
        }
    }
}
