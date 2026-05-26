package com.innercosmos.event;

import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class GravityRecalculateListener {
    private final MemoryCardMapper memoryCardMapper;

    public GravityRecalculateListener(MemoryCardMapper memoryCardMapper) {
        this.memoryCardMapper = memoryCardMapper;
    }

    @EventListener
    @Async("taskExecutor")
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            List<MemoryCard> cards = memoryCardMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryCard>()
                            .eq("user_id", event.userId)
                            .eq("status", "ACTIVE"));
            for (MemoryCard card : cards) {
                long days = card.lastTouchedAt != null
                        ? java.time.Duration.between(card.lastTouchedAt, LocalDateTime.now()).toDays()
                        : 30;
                double alpha = 0.40, beta = 0.25, gamma = 0.25, delta = 0.10, lambda = 0.05;
                double base = alpha * (card.intensityScore != null ? card.intensityScore : 0)
                        + beta * (card.recurrenceCount != null ? card.recurrenceCount : 0)
                        + gamma * (card.userImportance != null ? card.userImportance : 0)
                        + delta * (card.triggerCount != null ? card.triggerCount : 0);
                card.emotionalGravity = Math.log(1 + Math.max(base, 0)) * Math.exp(-lambda * Math.max(days, 0));
                card.updatedAt = LocalDateTime.now();
                memoryCardMapper.updateById(card);
            }
        } catch (Exception ignored) {
        }
    }
}
