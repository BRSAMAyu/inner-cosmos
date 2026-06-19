package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.MemorySettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NightlyMemorySettlementJob {
    private static final Logger log = LoggerFactory.getLogger(NightlyMemorySettlementJob.class);
    private final UserMapper userMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final GravityService gravityService;
    private final MemorySettlementService settlementService;
    private final EmotionBaselineService emotionBaselineService;
    private final EchoCapsuleMapper echoCapsuleMapper;

    // IC-CAP-002 B-4: nightly multiplicative decay toward floors.
    private static final double ENERGY_DECAY = 0.97;
    private static final double ENERGY_FLOOR = 0.3;
    private static final double FRESHNESS_DECAY = 0.95;
    private static final double FRESHNESS_FLOOR = 0.0;

    public NightlyMemorySettlementJob(UserMapper userMapper,
                                      MemoryCardMapper memoryCardMapper,
                                      GravityService gravityService,
                                      MemorySettlementService settlementService,
                                      EmotionBaselineService emotionBaselineService,
                                      EchoCapsuleMapper echoCapsuleMapper) {
        this.userMapper = userMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.gravityService = gravityService;
        this.settlementService = settlementService;
        this.emotionBaselineService = emotionBaselineService;
        this.echoCapsuleMapper = echoCapsuleMapper;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void nightlyRecalculation() {
        log.info("Nightly memory settlement started");
        List<User> users = userMapper.selectList(null);
        int failed = 0;
        for (User user : users) {
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
            } catch (Exception e) {
                failed++;
                log.error("Nightly settlement failed for user {}: {}", user.id, e.getMessage(), e);
            }
        }
        log.info("Nightly memory settlement completed for {} users ({} failed)", users.size(), failed);
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
            card.emotionalGravity = gravityService.calculateGravity(
                    card.intensityScore, card.recurrenceCount,
                    card.userImportance, card.triggerCount, 0);
            memoryCardMapper.updateById(card);
        }
    }
}
