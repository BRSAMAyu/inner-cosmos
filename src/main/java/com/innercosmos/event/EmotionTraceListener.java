package com.innercosmos.event;

import com.innercosmos.ai.semantic.EmotionInsight;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.service.EmotionInsightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * IC-EMO-001: on dialog finish, derive a single enriched emotion insight via the
 * semantic {@link EmotionInsightService} and upsert it as the session's
 * EmotionTrace. The old 6-keyword block + 1:1 weather map have been removed —
 * emotion derivation now lives entirely in EmotionInsightService (LLM primary +
 * deterministic lexicon fallback).
 */
@Component
public class EmotionTraceListener {
    private static final Logger log = LoggerFactory.getLogger(EmotionTraceListener.class);
    private final DialogMessageMapper messageMapper;
    private final EmotionInsightService emotionInsightService;

    public EmotionTraceListener(DialogMessageMapper messageMapper,
                                EmotionInsightService emotionInsightService) {
        this.messageMapper = messageMapper;
        this.emotionInsightService = emotionInsightService;
    }

    @EventListener
    @Async("taskExecutor")
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            var messages = messageMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.innercosmos.entity.DialogMessage>()
                            .eq("session_id", event.sessionId)
                            .eq("speaker", "USER")
                            .isNotNull("text_content"));
            StringBuilder allText = new StringBuilder();
            for (var msg : messages) {
                if (msg.textContent != null) allText.append(msg.textContent).append(" ");
            }
            String text = allText.toString();
            if (text.isBlank()) return;

            EmotionInsight insight = emotionInsightService.analyze(event.userId, text);
            emotionInsightService.writeTrace(event.userId, event.sessionId, insight);
        } catch (Exception e) {
            log.error("Event processing failed", e);
        }
    }
}
