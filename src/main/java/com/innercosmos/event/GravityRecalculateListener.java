package com.innercosmos.event;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.GravityTimePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "inner-cosmos.events.outbox.enabled", havingValue = "false", matchIfMissing = true)
public class GravityRecalculateListener {
    private static final Logger log = LoggerFactory.getLogger(GravityRecalculateListener.class);
    private final MemoryCardMapper memoryCardMapper;
    private final GravityService gravityService;
    private final GravityTimePolicy gravityTimePolicy;

    public GravityRecalculateListener(MemoryCardMapper memoryCardMapper, GravityService gravityService,
                                       GravityTimePolicy gravityTimePolicy) {
        this.memoryCardMapper = memoryCardMapper;
        this.gravityService = gravityService;
        this.gravityTimePolicy = gravityTimePolicy;
    }

    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("taskExecutor")
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            List<MemoryCard> cards = memoryCardMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryCard>()
                            .eq("user_id", event.userId)
                            .eq("status", "ACTIVE"));
            for (MemoryCard card : cards) {
                // Regression (Gemini audit 1.5): shared GravityTimePolicy/GravityService instead
                // of an inline formula copy with its own divergent (hardcoded 30-day) fallback --
                // see NightlyMemorySettlementJob#recalculateGravity, which now uses the same policy.
                long days = gravityTimePolicy.daysSinceAnchor(card);
                double gravity = gravityService.calculateGravity(
                        card.intensityScore != null ? card.intensityScore : 0,
                        card.recurrenceCount != null ? card.recurrenceCount : 0,
                        card.userImportance != null ? card.userImportance : 0,
                        card.triggerCount != null ? card.triggerCount : 0,
                        days);
                // Regression (Gemini audit 2.1, P0): field-level conditional update guarded on
                // versionNo instead of a whole-entity updateById(). This is a background-only
                // recompute (emotional_gravity + its own version bump); it must never carry a
                // stale userImportance/status/etc snapshot over the top of a concurrent user edit.
                // 0 rows affected means the card changed since this batch was read (most likely a
                // user edit, e.g. updateImportance) -- skip it for this event; the next event or
                // the nightly job will recompute against the fresh row. User edits always win.
                int updated = memoryCardMapper.update(null, new UpdateWrapper<MemoryCard>()
                        .eq("id", card.id).eq("version_no", card.versionNo)
                        .set("emotional_gravity", gravity)
                        .set("version_no", (card.versionNo == null ? 0 : card.versionNo) + 1)
                        .set("updated_at", LocalDateTime.now()));
                if (updated == 0) {
                    log.info("Memory card {} changed concurrently during gravity recompute; skipping this event, next cycle will retry", card.id);
                }
            }
        } catch (Exception e) {
            log.error("Event processing failed", e);
        }
    }
}
