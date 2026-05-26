package com.innercosmos.event;

import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class EmotionTraceListener {
    private static final Logger log = LoggerFactory.getLogger(EmotionTraceListener.class);
    private final DialogMessageMapper messageMapper;
    private final EmotionTraceMapper emotionTraceMapper;

    public EmotionTraceListener(DialogMessageMapper messageMapper, EmotionTraceMapper emotionTraceMapper) {
        this.messageMapper = messageMapper;
        this.emotionTraceMapper = emotionTraceMapper;
    }

    private static final Map<String, String[]> EMOTION_KEYWORDS = Map.of(
            "焦虑", new String[]{"焦虑", "担心", "不安", "紧张"},
            "自责", new String[]{"自责", "愧疚", "后悔", "不应该"},
            "沮丧", new String[]{"沮丧", "失望", "低落", "颓"},
            "愤怒", new String[]{"生气", "愤怒", "烦", "不满"},
            "喜悦", new String[]{"开心", "高兴", "顺利", "兴奋"},
            "疲惫", new String[]{"累", "疲惫", "撑不住", "倦"}
    );

    private static final Map<String, String> EMOTION_WEATHER = Map.of(
            "焦虑", "FOGGY",
            "自责", "RAINY",
            "沮丧", "STORM",
            "愤怒", "STORM",
            "喜悦", "SUNNY",
            "疲惫", "CLOUDY"
    );

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

            String bestEmotion = null;
            int bestCount = 0;
            for (var entry : EMOTION_KEYWORDS.entrySet()) {
                int count = 0;
                for (String kw : entry.getValue()) {
                    int idx = 0;
                    while ((idx = text.indexOf(kw, idx)) != -1) { count++; idx += kw.length(); }
                }
                if (count > bestCount) { bestCount = count; bestEmotion = entry.getKey(); }
            }
            if (bestEmotion == null) return;

            EmotionTrace trace = new EmotionTrace();
            trace.userId = event.userId;
            trace.sourceSessionId = event.sessionId;
            trace.emotionName = bestEmotion;
            trace.emotionScore = Math.min(10.0, bestCount * 2.0);
            trace.weatherType = EMOTION_WEATHER.getOrDefault(bestEmotion, "CLOUDY");
            trace.triggerScene = "对话关键词提取";
            trace.recordDate = LocalDate.now();
            trace.createdAt = LocalDateTime.now();
            trace.updatedAt = LocalDateTime.now();
            emotionTraceMapper.insert(trace);
        } catch (Exception e) {
            log.error("Event processing failed", e);
        }
    }
}
