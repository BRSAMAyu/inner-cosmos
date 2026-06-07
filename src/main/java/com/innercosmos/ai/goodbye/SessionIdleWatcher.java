package com.innercosmos.ai.goodbye;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.mapper.DialogSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled job that checks for idle sessions (no activity for 30+ minutes)
 * and triggers goodbye flow automatically.
 */
@Component
public class SessionIdleWatcher {

    @Autowired
    private DialogSessionMapper sessionMapper;

    @Autowired
    private GoodbyeOrchestrator goodbye;

    @Scheduled(fixedDelay = 5 * 60 * 1000) // every 5 minutes
    public void scan() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        var idleSessions = sessionMapper.selectList(
                new QueryWrapper<DialogSession>()
                        .eq("status", "open")
                        .lt("updated_at", cutoff)
        );

        for (var sess : idleSessions) {
            // Guard: skip if session already closed or has goodbyeTrigger
            if (sess.endedAt != null || sess.goodbyeTrigger != null) {
                continue;
            }
            try {
                goodbye.start(sess.userId, sess.id, "IDLE");
            } catch (Exception e) {
                // Log and continue with next session
            }
        }
    }
}