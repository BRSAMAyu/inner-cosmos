package com.innercosmos.ai.proactive;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.ai.proactive.dto.ProactiveCandidate;

/**
 * Matches recent events to generate proactive candidate triggers.
 */
@Component
public class EventTriggerMatcher {

    @Autowired
    private EmotionTraceMapper emotionMapper;

    @Autowired
    private TodoItemMapper todoMapper;

    public List<ProactiveCandidate> candidates(Long userId, Duration lookback) {
        LocalDateTime since = LocalDateTime.now().minus(lookback);
        List<ProactiveCandidate> out = new ArrayList<>();

        // 1) Mood drop: check recent emotion traces
        var emotions = emotionMapper.selectList(
            new QueryWrapper<EmotionTrace>()
                .eq("user_id", userId)
                .ge("created_at", since)
                .orderByDesc("created_at")
                .last("LIMIT 5")
        );
        if (!emotions.isEmpty()) {
            double latest = emotions.get(0).emotionScore != null ? emotions.get(0).emotionScore : 0;
            double avg = emotions.stream()
                .filter(e -> e.emotionScore != null)
                .mapToDouble(e -> e.emotionScore)
                .average().orElse(latest);
            if (latest < avg - 1.5) {
                out.add(new ProactiveCandidate("mood_drop", String.valueOf(latest)));
            }
        }

        // 2) Todo completed
        var completed = todoMapper.selectList(
            new QueryWrapper<TodoItem>()
                .eq("user_id", userId)
                .eq("status", "COMPLETED")
                .ge("updated_at", since)
                .last("LIMIT 1")
        );
        if (!completed.isEmpty()) {
            out.add(new ProactiveCandidate("todo_completed", completed.get(0).taskName));
        }

        // 3) Todo upcoming within 15 minutes
        LocalDateTime in15min = LocalDateTime.now().plusMinutes(15);
        var upcoming = todoMapper.selectList(
            new QueryWrapper<TodoItem>()
                .eq("user_id", userId)
                .eq("status", "PENDING")
                .le("deadline", in15min)
                .gt("deadline", LocalDateTime.now())
                .last("LIMIT 1")
        );
        if (!upcoming.isEmpty()) {
            out.add(new ProactiveCandidate("todo_upcoming", upcoming.get(0).taskName));
        }

        return out;
    }
}