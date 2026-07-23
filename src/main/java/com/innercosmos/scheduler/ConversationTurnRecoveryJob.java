package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.mapper.ConversationTurnMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Settles turns orphaned by a hard JVM/node failure. Normal Pod deletion is handled by
 * graceful drain; this job is the slower, durable safety net when no client reconnects.
 */
@Component
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or "
        + "'${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class ConversationTurnRecoveryJob {
    private static final Logger log = LoggerFactory.getLogger(ConversationTurnRecoveryJob.class);

    private final ConversationTurnMapper turnMapper;
    private final ConversationChoreographyService choreographyService;
    private final Duration staleAfter;
    private final int batchSize;

    public ConversationTurnRecoveryJob(
            ConversationTurnMapper turnMapper,
            ConversationChoreographyService choreographyService,
            @Value("${inner-cosmos.aurora.turn-recovery.stale-after:PT5M}") Duration staleAfter,
            @Value("${inner-cosmos.aurora.turn-recovery.batch-size:50}") int batchSize) {
        this.turnMapper = turnMapper;
        this.choreographyService = choreographyService;
        this.staleAfter = staleAfter.isNegative() || staleAfter.isZero() ? Duration.ofMinutes(5) : staleAfter;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Scheduled(fixedDelayString = "${inner-cosmos.aurora.turn-recovery.poll-delay-ms:60000}")
    public void recoverOrphanedTurns() {
        LocalDateTime cutoff = LocalDateTime.now().minus(staleAfter);
        List<ConversationTurn> candidates = turnMapper.selectList(new QueryWrapper<ConversationTurn>()
                .in("status", List.of("GENERATING", "PLANNED", "STREAMING", "PARTIAL"))
                .lt("updated_at", cutoff)
                .orderByAsc("updated_at")
                .last("LIMIT " + batchSize));
        for (ConversationTurn candidate : candidates) {
            try {
                choreographyService.interruptIfStale(
                        candidate.userId, candidate.id, cutoff, "STREAM_ORPHANED_BY_RUNTIME_FAILURE");
            } catch (RuntimeException recoveryFailure) {
                // One malformed/racing row must not prevent later orphaned turns from settling.
                log.warn("Could not reconcile orphaned conversation turn {}: {}",
                        candidate.id, recoveryFailure.getMessage());
            }
        }
    }
}
