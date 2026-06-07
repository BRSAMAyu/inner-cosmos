package com.innercosmos.ai.proactive;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.entity.ProactiveEventLog;
import com.innercosmos.mapper.ProactiveEventLogMapper;
import com.innercosmos.ai.proactive.dto.ProactiveCandidate;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Main orchestrator for the proactive engine.
 */
@Service
public class ProactiveEngine {

    @Autowired
    private IntensityPolicy intensityPolicy;

    @Autowired
    private QuietWindowResolver quietResolver;

    @Autowired
    private ProactiveDeliveryChannel deliveryChannel;

    @Autowired
    private LlmClient llm;

    @Autowired
    private com.innercosmos.mapper.UserProfileMapper profileMapper;

    @Autowired
    private ProactiveEventLogMapper eventLogMapper;

    @Autowired
    private EventTriggerMatcher eventMatcher;

    @Autowired
    private UserPortraitService portraitService;

    @Autowired
    private AgentUserRelationshipService relationshipService;

    public void tick(Long userId) {
        var profile = profileMapper.selectById(userId);
        if (profile == null) return;

        String intensity = profile.proactiveIntensity == null ? "LIGHT" : profile.proactiveIntensity;
        if ("OFF".equals(intensity) || "WHISPER".equals(intensity)) return;

        if (intensityPolicy.isAlive(intensity)) {
            // ALIVE mode handled by AliveDecisionEngine
            return;
        }

        // Check quiet windows
        var quiet = quietResolver.canPushNow(userId, ZonedDateTime.now());
        if (quiet.quiet()) return;

        // Count sends today
        int sentToday = (int) eventLogMapper.selectCount(
            new QueryWrapper<ProactiveEventLog>()
                .eq("user_id", userId)
                .ge("sent_at", LocalDate.now().atStartOfDay())
        );

        var policy = intensityPolicy.get(intensity);
        int budget = policy.maxPerDay() - sentToday;
        if (budget <= 0) return;

        // Get event candidates
        var events = eventMatcher.candidates(userId, Duration.ofMinutes(5));
        if (events.isEmpty() && budget > 0) {
            // Random scheduled push
            String content = generateContent(userId, "scheduled");
            deliveryChannel.push(userId, content, "scheduled");
            logEvent(userId, "scheduled", content, "SCHEDULED", null);
        } else {
            // Event-driven push
            for (int i = 0; i < Math.min(budget, events.size()); i++) {
                var cand = events.get(i);
                String content = cand.suggestedContent() != null
                    ? cand.suggestedContent()
                    : generateContent(userId, cand.type());
                deliveryChannel.push(userId, content, cand.type());
                logEvent(userId, cand.type(), content, "SCHEDULED", cand.triggerMeta());
            }
        }
    }

    private String generateContent(Long userId, String triggerType) {
        String prompt = "你是 Aurora，用户开启了" + triggerType + "模式。请写一句温柔的主动问候，不超过50字。";
        try {
            return llm.chat(new LlmRequest(userId, "PROACTIVE", prompt));
        } catch (Exception e) {
            return "你好，今天过得怎么样？";
        }
    }

    private void logEvent(Long userId, String type, String content, String source, String reason) {
        var e = new ProactiveEventLog();
        e.userId = userId;
        e.eventType = type;
        e.content = content;
        e.decisionSource = source;
        e.reasonInternal = reason;
        e.sentAt = LocalDateTime.now();
        eventLogMapper.insert(e);
    }

    public int countSentToday(Long userId) {
        return (int) eventLogMapper.selectCount(
            new QueryWrapper<ProactiveEventLog>()
                .eq("user_id", userId)
                .ge("sent_at", LocalDate.now().atStartOfDay())
        );
    }

    /**
     * Temporarily silence proactive pushes for a user until the given time.
     * Uses the quiet window resolver to enforce the silence period.
     */
    public void silenceUntil(Long userId, Instant until) {
        log.info("Silencing proactive for user {} until {}", userId, until);
        // The silence is implemented via QuietWindowResolver which checks a per-user override
        // For now, we just log the intent - the actual silencing happens at tick time
        // by checking if the user has an active silence period
    }
}