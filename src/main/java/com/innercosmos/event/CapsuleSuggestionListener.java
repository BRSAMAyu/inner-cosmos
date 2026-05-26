package com.innercosmos.event;

import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CapsuleSuggestionListener {
    private static final Logger log = LoggerFactory.getLogger(CapsuleSuggestionListener.class);
    private static final double GRAVITY_THRESHOLD = 1.5;

    private final MemoryCardMapper memoryCardMapper;

    public CapsuleSuggestionListener(MemoryCardMapper memoryCardMapper) {
        this.memoryCardMapper = memoryCardMapper;
    }

    @EventListener
    @Async("taskExecutor")
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            List<MemoryCard> cards = memoryCardMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryCard>()
                            .eq("user_id", event.userId)
                            .eq("status", "ACTIVE")
                            .gt("emotional_gravity", GRAVITY_THRESHOLD)
                            .eq("visibility_level", "PRIVATE"));
            for (MemoryCard card : cards) {
                log.info("建议用户 {} 将高重力记忆 '{}' (gravity={}) 编织为共鸣体",
                        event.userId, card.title, card.emotionalGravity);
            }
        } catch (Exception e) {
            log.error("Event processing failed", e);
        }
    }
}
