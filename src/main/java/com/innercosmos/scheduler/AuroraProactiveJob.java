package com.innercosmos.scheduler;

import com.innercosmos.ai.proactive.ProactiveEngine;
import com.innercosmos.ai.proactive.AliveDecisionEngine;
import com.innercosmos.ai.proactive.ProactiveDeliveryChannel;
import com.innercosmos.entity.PrivateTimer;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.PrivateTimerMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that runs the proactive engine every 5 minutes.
 */
@Component
public class AuroraProactiveJob {

    private static final Logger log = LoggerFactory.getLogger(AuroraProactiveJob.class);

    @Autowired
    private ProactiveEngine engine;

    @Autowired
    private AliveDecisionEngine aliveEngine;

    @Autowired
    private ProactiveDeliveryChannel deliveryChannel;

    @Autowired
    private UserProfileMapper userMapper;

    @Autowired
    private PrivateTimerMapper timerMapper;

    @Scheduled(fixedDelay = 5 * 60 * 1000) // every 5 minutes
    public void run() {
        // 1) Iterate active users and tick each
        var users = userMapper.selectList(new QueryWrapper<UserProfile>());
        for (var u : users) {
            try {
                if ("ALIVE".equalsIgnoreCase(u.proactiveIntensity)) {
                    aliveEngine.tick(u.id);
                } else {
                    engine.tick(u.id);
                }
            } catch (Exception e) {
                // M-043: actually log (was silently swallowed), then continue with the next user.
                log.warn("Proactive tick failed for user {}: {}", u.id, e.getMessage());
            }
        }

        // 2) Fire any private timers that are due
        var due = timerMapper.selectList(
            new QueryWrapper<PrivateTimer>()
                .isNull("cancelled_at")
                .isNull("fired_at")
                .le("fire_at", LocalDateTime.now())
        );
        for (var t : due) {
            try {
                if (t.content != null && !t.content.isEmpty()) {
                    deliveryChannel.push(t.userId, t.content, "alive_internal");
                }
                t.firedAt = LocalDateTime.now();
                timerMapper.updateById(t);
            } catch (Exception e) {
                // M-043: a transient delivery failure should NOT permanently lose the timer —
                // leave firedAt null so the next tick retries it. Log instead of swallowing.
                log.warn("Private timer {} delivery failed (will retry next tick): {}", t.id, e.getMessage());
            }
        }
    }
}