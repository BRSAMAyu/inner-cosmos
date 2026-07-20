package com.innercosmos.ai.proactive;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.entity.PrivateTimer;
import com.innercosmos.entity.ProactiveEventLog;
import com.innercosmos.mapper.PrivateTimerMapper;
import com.innercosmos.mapper.ProactiveEventLogMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.service.WakeIntentService;
import com.innercosmos.ai.proactive.dto.AliveDecision;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-driven decision engine for ALIVE mode.
 * Uses the LLM to decide whether to push, wait, or schedule.
 */
@Component
public class AliveDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(AliveDecisionEngine.class);
    private static final int MAX_CONSECUTIVE_PUSHES_PER_HOUR = 10;

    @Autowired
    private LlmClient llm;

    @Autowired
    private QuietWindowResolver quietResolver;

    @Autowired
    private PrivateTimerMapper timerMapper;

    @Autowired(required = false)
    private WakeIntentService wakeIntentService;

    @Autowired
    private UserProfileMapper profileMapper;

    @Autowired
    private ProactiveEventLogMapper eventLogMapper;

    @Autowired
    private ProactiveDeliveryChannel deliveryChannel;

    @Autowired
    private UserPortraitService portraitService;

    @Autowired
    private AgentUserRelationshipService relationshipService;

    private final ObjectMapper om = new ObjectMapper();
    private Clock clock = Clock.systemUTC();

    public void tick(Long userId) {
        ZoneId userZone = resolveUserZone(userId);
        var q = quietResolver.canPushNow(userId, ZonedDateTime.now(clock).withZoneSameInstant(userZone));
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
                    Instant preferred = clock.instant().plus(Duration.ofMinutes(decision.waitMinutes()));
                    if (wakeIntentService != null && decision.contentForUser() != null
                            && !decision.contentForUser().isBlank()) {
                        wakeIntentService.scheduleAtInstants(userId, "Aurora 想在合适的时候继续这段陪伴",
                            "Aurora 计划回来看看你", decision.contentForUser(), preferred.minus(Duration.ofMinutes(5)),
                            preferred, preferred.plus(Duration.ofHours(6)), userZone.getId(), "alive-decision");
                    } else {
                        // Compatibility path for old tests/data and malformed provider output.
                        PrivateTimer timer = new PrivateTimer();
                        timer.userId = userId;
                        timer.fireAt = LocalDateTime.ofInstant(preferred, ZoneOffset.UTC);
                        timer.kind = "ALIVE_INTERNAL";
                        timer.content = decision.contentForUser();
                        timerMapper.insert(timer);
                    }
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
        String now = clock.instant().toString();
        String portraitSummary = summary(portraitService.getAll(userId));
        String relSummary = summary(relationshipService.getOrInit(userId));
        String recentLogs = recentProactiveLog(userId);

        return String.format("""
           你是 Aurora。用户当前开启了 ALIVE 模式。
            请决定这一刻是否要主动发起对话。
            只输出严格 JSON:
            {"decide":"push|wait|schedule", "wait_minutes":N, "content_for_user":"...", "reason":"..."}
            wait_minutes ∈ [5, 1440]。push 与 schedule 都必须填写 content_for_user；
            content_for_user 最多 800 字，不得诊断、制造依赖或虚构你没有看到的事实。
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
            String decide = node.has("decide")
                    ? node.get("decide").asText("wait").trim().toLowerCase(Locale.ROOT) : "wait";
            if (!Set.of("push", "wait", "schedule").contains(decide)) {
                return AliveDecision.wait(30, "invalid_decision");
            }
            int waitMinutes = Math.max(5, Math.min(1440,
                    node.has("wait_minutes") ? node.get("wait_minutes").asInt(30) : 30));
            String content = node.has("content_for_user") ? node.get("content_for_user").asText("").strip() : "";
            if (content.length() > 800) content = content.substring(0, 800);
            String reason = node.has("reason") ? node.get("reason").asText("").strip() : "";
            if (reason.length() > 240) reason = reason.substring(0, 240);
            if (("push".equals(decide) || "schedule".equals(decide)) && content.isBlank()) {
                return AliveDecision.wait(waitMinutes, "missing_user_content");
            }
            return new AliveDecision(decide, waitMinutes, content, reason);
        } catch (Exception e) {
            // Never log model output here: it can echo portrait, relationship or proactive-history
            // content. Length is enough to diagnose a contract failure without leaking P0/P1 data.
            log.warn("Failed to parse ALIVE decision JSON (responseLength={})", raw == null ? 0 : raw.length());
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
        // No daytime gate: any time of day, if nothing has gone out today and the last
        // push was more than 2h ago, send a gentle check-in so the demo always shows life.
        if (todayPushCount(userId) == 0 && hoursSinceLastPush(userId) >= 2) {
            deliveryChannel.push(userId, "嘿，我突然想起你，今天过得还好吗？", "alive_minimum");
            logEvent(userId, "ALIVE_MINIMUM", "嘿，我突然想起你，今天过得还好吗？", "forced_checkin");
        }
    }

    /**
     * Hours since the user's most recent proactive push of any kind.
     * Returns a large number (effectively "long ago") if there has never been one.
     */
    private long hoursSinceLastPush(Long userId) {
        var last = eventLogMapper.selectList(
            new QueryWrapper<ProactiveEventLog>()
                .eq("user_id", userId)
                .orderByDesc("sent_at")
                .last("LIMIT 1")
        );
        if (last.isEmpty() || last.get(0).sentAt == null) return Long.MAX_VALUE;
        return Duration.between(last.get(0).sentAt, LocalDateTime.now()).toHours();
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

    private ZoneId resolveUserZone(Long userId) {
        if (profileMapper != null) {
            var profiles = profileMapper.selectList(new QueryWrapper<UserProfile>()
                .eq("user_id", userId).last("LIMIT 1"));
            if (!profiles.isEmpty() && profiles.getFirst().timezone != null) {
                try { return ZoneId.of(profiles.getFirst().timezone); }
                catch (RuntimeException invalid) {
                    log.warn("Ignoring invalid persisted timezone for user {}", userId);
                }
            }
        }
        return ZoneId.of("Asia/Singapore");
    }

    void useClock(Clock fixedClock) {
        this.clock = fixedClock;
    }
}
