package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
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

    public NightlyMemorySettlementJob(UserMapper userMapper,
                                      MemoryCardMapper memoryCardMapper,
                                      GravityService gravityService,
                                      MemorySettlementService settlementService) {
        this.userMapper = userMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.gravityService = gravityService;
        this.settlementService = settlementService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void nightlyRecalculation() {
        log.info("Nightly memory settlement started");
        List<User> users = userMapper.selectList(null);
        for (User user : users) {
            recalculateGravity(user.id);
            settlementService.updateThemeAggregation(user.id);
        }
        log.info("Nightly memory settlement completed for {} users", users.size());
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
