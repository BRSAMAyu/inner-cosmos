package com.innercosmos.ai.proactive;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.entity.PrivateTimer;
import com.innercosmos.entity.ProactiveEventLog;
import com.innercosmos.mapper.PrivateTimerMapper;
import com.innercosmos.mapper.ProactiveEventLogMapper;
import com.innercosmos.ai.proactive.dto.AliveDecision;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-driven decision engine for ALIVE mode.
 * Uses the LLM to decide whether to push, wait, or schedule.
 */
@Component
public class AliveDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(AliveDecisionEngine.class);
    private static final int MAX_CONSECUTIVE_PUSHES_PER_HOUR = 4;

    @Autowired
    private LlmClient llm;

    @Autowired
    private QuietWindowResolver quietResolver;

    @Autowired
    private PrivateTimerMapper timerMapper;

    @Autowired
    private ProactiveEventLogMapper eventLogMapper;

    @Autowired
    private ProactiveDeliveryChannel deliveryChannel;

    @Autowired
    private UserPortraitService portraitService;

    @Autowired
    private AgentUserRelationshipService relationshipService;

    private final ObjectMapper om = new ObjectMapper();

    public void tick(Long userId) {
        var q = quietResolver.canPushNow(userId, ZonedDateTime.now());
        if (q.quiet()) return;

        // Hard cap check
        if (recentPushCountInHour(userId) >= MAX_CONSECUTIVE_PUSHES_PER_HOUR) {
            log.debug("ALIVE hard cap reached for user {}", userId);
            return;
        }

        String prompt = buildPrompt(userId);
        LlmRequest req = new LlmRequest(userId, "ALIVE_DECISION", prompt);
        String raw;
        try {
            raw = llm.chat(req);
        } catch (Exception e) {
            log.warn("ALIVE decision failed for user {}: {}", userId, e.getMessage(), e);
            try {
                raw = llm.chat(req);
            } catch (Exception retryEx) {
                log.warn("ALIVE decision retry also failed for user {}: {}", userId, retryEx.getMessage(), retryEx);
                return;
            }
        }
        try {
            var decision = parse(raw);

            switch (decision.decide()) {
                case "push" -> {
                    deliveryChannel.push(userId, decision.contentForUser(), "alive_push");
                    logEvent(userId, "ALIVE_LLM", decision.contentForUser(), decision.reasonInternal());
                }
                case "schedule" -> {
                    PrivateTimer timer = new PrivateTimer();
                    timer.userId = userId;
                    timer.fireAt = LocalDateTime.now().plusMinutes(decision.waitMinutes());
                    timer.kind = "ALIVE_INTERNAL";
                    timerMapper.insert(timer);
                }
                case "wait" -> {
                    // Ensure minimum daily push
                    ensureMinDailyPush(userId);
                }
            }
        } catch (Exception e) {
            log.warn("ALIVE decision processing failed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    private String buildPrompt(Long userId) {
        String now = Instant.now().toString();
        String portraitSummary = summary(portraitService.getAll(userId));
        String relSummary = summary(relationshipService.getOrInit(userId));
        String recentLogs = recentProactiveLog(userId);

        return String.format("""
           你是 Aurora。用户当前开启了 ALIVE 模式。
            请决定这一刻是否要主动发起对话。
            只输出严格 JSON:
            {"decide":"push|wait|schedule", "wait_minutes":N, "content_for_user":"...", "reason":"..."}
            wait_minutes ∈ [5, 1440]。push 时必须填 content_for_user。
            当前时间: %s
            用户画像: %s
            关系状态: %s
            最近 7d 主动式日志: %s
            """, now, portraitSummary, relSummary, recentLogs);
    }

    private AliveDecision parse(String raw) {
        try {
            // Try to extract JSON from response
            String json = raw.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end >= 0) {
                json = json.substring(start, end + 1);
            }
            JsonNode node = om.readTree(json);
            String decide = node.has("decide") ? node.get("decide").asText() : "wait";
            int waitMinutes = node.has("wait_minutes") ? node.get("wait_minutes").asInt() : 30;
            String content = node.has("content_for_user") ? node.get("content_for_user").asText() : "";
            String reason = node.has("reason") ? node.get("reason").asText() : "";
            return new AliveDecision(decide, waitMinutes, content, reason);
        } catch (Exception e) {
            log.warn("Failed to parse ALIVE decision JSON: {}", raw);
            return AliveDecision.wait(30, "parse_error");
        }
    }

    private int recentPushCountInHour(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        return Math.toIntExact(eventLogMapper.selectCount(
            new QueryWrapper<ProactiveEventLog>()
                .eq("user_id", userId)
                .eq("event_type", "alive_push")
                .ge("sent_at", since)
        ));
    }

    private int todayPushCount(Long userId) {
        return Math.toIntExact(eventLogMapper.selectCount(
            new QueryWrapper<ProactiveEventLog>()
                .eq("user_id", userId)
                .ge("sent_at", LocalDate.now().atStartOfDay())
        ));
    }

    private void ensureMinDailyPush(Long userId) {
        if (todayPushCount(userId) == 0 && ZonedDateTime.now().getHour() >= 19) {
            // Force a gentle evening push
            deliveryChannel.push(userId, "今天要结束了，你还好吗？", "alive_minimum");
            logEvent(userId, "ALIVE_MINIMUM", "今天要结束了，你还好吗？", "forced_evening");
        }
    }

    private String summary(Object obj) {
        if (obj == null) return "无数据";
        return obj.toString().replace("\n", " ").substring(0, Math.min(200, obj.toString().length()));
    }

    private String recentProactiveLog(Long userId) {
        var logs = eventLogMapper.selectList(
            new QueryWrapper<ProactiveEventLog>()
                .eq("user_id", userId)
                .ge("sent_at", LocalDate.now().minusDays(7).atStartOfDay())
                .orderByDesc("sent_at")
                .last("LIMIT 10")
        );
        return logs.stream()
            .map(e -> e.eventType + ":" + e.content)
            .reduce((a, b) -> a + "; " + b)
            .orElse("无");
    }

    private void logEvent(Long userId, String type, String content, String reason) {
        var e = new ProactiveEventLog();
        e.userId = userId;
        e.eventType = type;
        e.content = content;
        e.decisionSource = "ALIVE";
        e.reasonInternal = reason;
        e.sentAt = LocalDateTime.now();
        eventLogMapper.insert(e);
    }
}