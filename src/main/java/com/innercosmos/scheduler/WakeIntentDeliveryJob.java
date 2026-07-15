package com.innercosmos.scheduler;

import com.innercosmos.ai.proactive.ProactiveDeliveryChannel;
import com.innercosmos.ai.proactive.QuietWindowResolver;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.safety.SafetyBoundaryFilter;
import com.innercosmos.service.WakeIntentService;
import com.innercosmos.service.WakeIntentRelevanceEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Processes durable return intents with a per-row lease, independently of scheduler-wide locks. */
@Component
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class WakeIntentDeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(WakeIntentDeliveryJob.class);
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

    private final WakeIntentService intents;
    private final QuietWindowResolver quietWindow;
    private final ProactiveDeliveryChannel liveChannel;
    private final SafetyBoundaryFilter safety;
    private final UserProfileMapper profiles;
    private final WakeIntentRelevanceEvaluator relevance;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    public WakeIntentDeliveryJob(WakeIntentService intents, QuietWindowResolver quietWindow,
                                 ProactiveDeliveryChannel liveChannel,
                                 SafetyBoundaryFilter safety, UserProfileMapper profiles,
                                 WakeIntentRelevanceEvaluator relevance) {
        this.intents = intents;
        this.quietWindow = quietWindow;
        this.liveChannel = liveChannel;
        this.safety = safety;
        this.profiles = profiles;
        this.relevance = relevance;
    }

    @Scheduled(fixedDelayString = "${inner-cosmos.wake-intent.poll-delay-ms:30000}")
    public void run() {
        intents.expirePastDue();
        for (WakeIntent intent : intents.claimDue(workerId, 50, CLAIM_LEASE)) {
            try {
                decide(intent);
            } catch (RuntimeException failure) {
                // A crashed/failed worker deliberately leaves the lease for another replica.
                log.warn("Wake intent {} decision failed; lease will recover it: {}", intent.id, failure.getMessage());
            }
        }
    }

    void decide(WakeIntent intent) {
        if (intents.isPurposeMuted(intent.userId, intent.purpose)) {
            intents.finish(intent, "DROP", "user_muted_similar_returns");
            return;
        }
        var current = relevance.evaluate(intent);
        if (!current.relevant()) {
            intents.finish(intent, "DROP", current.reason());
            return;
        }
        if (intent.content == null || intent.content.isBlank()) {
            intents.finish(intent, "DROP", "empty_payload");
            return;
        }
        var risk = safety.inspect(intent.content);
        if (risk.matched) {
            intents.finish(intent, "DROP", "risk:" + risk.riskType);
            return;
        }
        // An autonomous ALIVE return remains subordinate to the user's latest proactive contract.
        if ("alive-decision".equals(intent.payloadRef)) {
            var rows = profiles.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserProfile>()
                .eq("user_id", intent.userId).last("LIMIT 1"));
            if (!rows.isEmpty() && "OFF".equalsIgnoreCase(rows.getFirst().proactiveIntensity)) {
                intents.finish(intent, "DROP", "user_proactive_preference_off");
                return;
            }
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(intent.timezone == null ? "Asia/Shanghai" : intent.timezone);
        } catch (RuntimeException invalidZone) {
            zone = ZoneId.of("Asia/Shanghai");
        }
        QuietWindowResolver.Reason boundary = quietWindow.canPushNow(intent.userId, ZonedDateTime.now(zone));
        LocalDateTime next = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15);
        if (boundary.quiet() && next.isBefore(intent.latestAt)) {
            intents.delay(intent, next, "boundary:" + boundary.cause());
            return;
        }

        // Persist the return before best-effort live fan-out. This survives API/scheduler split,
        // browser disconnects and restarts; the SSE signal only improves immediacy.
        boolean live = liveChannel.hasActiveEmitter(intent.userId);
        boolean completed = intents.finishWithNotification(intent,
            live ? "SEND_AND_IN_APP" : "CONVERT_TO_IN_APP",
            boundary.quiet() ? "latest_window_boundary" : (live ? "live_and_durable" : "user_offline"),
            intent.reasonForUser, intent.content);
        if (completed && live) liveChannel.push(intent.userId, intent.content, "wake_intent");
    }
}
