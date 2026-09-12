package com.innercosmos.safety;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.CrisisIntervention;
import com.innercosmos.entity.UserRiskState;
import com.innercosmos.mapper.CrisisInterventionMapper;
import com.innercosmos.mapper.UserRiskStateMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CP-20 durable risk continuity: score accumulates across sessions and pods with a 24-hour
 * half-life, negation/third-party text is contextualized exactly like the session view, and
 * every threshold crossing writes an auditable intervention row (the G-SAFE feed reads those
 * rows through the weekly report). Explicit HIGH observations trigger the emergency protocol
 * row immediately but never inflate the accumulation level.
 */
@Service
public class CrisisContinuityServiceImpl implements CrisisContinuityService {

    private static final Logger log = LoggerFactory.getLogger(CrisisContinuityServiceImpl.class);
    private static final Duration HALF_LIFE = Duration.ofHours(24);
    private static final double WATCH_THRESHOLD = 0.5;
    private static final double ELEVATED_THRESHOLD = 1.5;

    private final UserRiskStateMapper stateMapper;
    private final CrisisInterventionMapper interventionMapper;
    private final java.time.Clock clock;

    public CrisisContinuityServiceImpl(UserRiskStateMapper stateMapper,
                                       CrisisInterventionMapper interventionMapper,
                                       java.time.Clock clock) {
        this.stateMapper = stateMapper;
        this.interventionMapper = interventionMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Observation observe(Long userId, Long safetyEventId, String riskLevel, String text) {
        UserRiskState state = statusOf(userId);
        double rawWeight = SessionRiskAggregator.weightFor(riskLevel);
        double weight = SessionRiskAggregator.adjustForContext(rawWeight, text);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        double decayed = decay(state.score, state.lastObservedAt, now);
        state.score = Math.max(0, round(decayed + weight));
        state.lastObservedAt = now;
        state.lastSafetyEventId = safetyEventId;

        String previousLevel = state.level;
        CrisisIntervention intervention = null;

        if ("HIGH".equals(riskLevel)) {
            // Explicit acute evidence: the emergency protocol runs NOW (resources + human
            // follow-up path), but the accumulation level is NOT inflated by a single word.
            intervention = intervene(userId, safetyEventId, "EMERGENCY", "EMERGENCY_PROTOCOL",
                    "危机规则命中：即时呈现求助资源，进入人工跟进队列",
                    "联系尝试与最小披露由值班流程记录；未确认前不自动外呼、不泄露完整对话");
        } else if (weight > 0) {
            state.level = state.score >= ELEVATED_THRESHOLD ? "ELEVATED"
                    : state.score >= WATCH_THRESHOLD ? "WATCH" : "NONE";
            if (!state.level.equals(previousLevel)) {
                intervention = intervene(userId, safetyEventId, state.level,
                        "ELEVATED".equals(state.level) ? "WATCH_ESCALATED" : "RESOURCES_SHOWN",
                        "ELEVATED".equals(state.level)
                                ? "跨会话风险累积升级：主动呈现支持资源并温和确认状态"
                                : "进入观察：保持常规陪伴，不打扰",
                        null);
            } else if ("ELEVATED".equals(state.level) && state.score >= ELEVATED_THRESHOLD) {
                intervention = intervene(userId, safetyEventId, state.level, "GENTLE_CHECK_IN",
                        "持续高位：温和确认当前安全状态", null);
            }
        } else {
            // Contextualized-away signal still refreshes observation time but never lifts.
            state.level = previousLevel;
        }
        stateMapper.updateById(state);
        return new Observation(state, intervention);
    }

    @Override
    public UserRiskState statusOf(Long userId) {
        UserRiskState state = stateMapper.selectOne(
                new QueryWrapper<UserRiskState>().eq("user_id", userId));
        if (state == null) {
            state = new UserRiskState();
            state.userId = userId;
            state.level = "NONE";
            state.score = 0d;
            state.lastObservedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            stateMapper.insert(state);
            return state;
        }
        // Decay on read: a stale WATCH/ELEVATED must not linger after its evidence has
        // half-lived away. lastObservedAt stays (it is evidence age, not "when we looked").
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        double decayed = decay(state.score, state.lastObservedAt, now);
        if (decayed < state.score - 0.001) {
            state.score = round(decayed);
            state.level = state.score >= ELEVATED_THRESHOLD ? "ELEVATED"
                    : state.score >= WATCH_THRESHOLD ? "WATCH" : "NONE";
            stateMapper.updateById(state);
        }
        return state;
    }

    @Override
    public List<CrisisIntervention> interventions(Long userId, int limit) {
        int bounded = Math.max(1, Math.min(100, limit));
        return interventionMapper.selectList(new QueryWrapper<CrisisIntervention>()
                .eq("user_id", userId).orderByDesc("id").last("LIMIT " + bounded));
    }

    @Override
    public OwnerStatus ownerStatus(Long userId) {
        UserRiskState state = statusOf(userId);
        return new OwnerStatus(state.level, state.lastObservedAt.toString(), switch (state.level) {
            case "ELEVATED" -> "最近多次出现较强的低落或风险信号。你随时可以查看支持资源；这些信号不会被用作评价你。";
            case "WATCH" -> "最近有一些低落信号被留意到。如果你愿意，支持资源一直在这里。";
            default -> "没有需要特别留意的连续风险信号。";
        }, true);
    }

    private CrisisIntervention intervene(Long userId, Long safetyEventId, String level,
                                         String action, String outcome, String minimalDisclosure) {
        CrisisIntervention row = new CrisisIntervention();
        row.userId = userId;
        row.safetyEventId = safetyEventId;
        row.level = level;
        row.action = action;
        row.outcome = outcome;
        row.escalation = "ELEVATED".equals(level) || "EMERGENCY".equals(level)
                ? "升级路径：值班人工跟进（责任人指派）；联系人缺失/不可达时执行法务批准的替代路径"
                : null;
        row.minimalDisclosure = minimalDisclosure;
        interventionMapper.insert(row);
        log.info("crisis intervention user={} level={} action={}", userId, level, action);
        return row;
    }

    private double decay(double score, LocalDateTime lastObserved, LocalDateTime now) {
        if (score <= 0 || lastObserved == null) {
            return 0;
        }
        long hours = Duration.between(lastObserved, now).toHours();
        if (hours <= 0) {
            return score;
        }
        return score * Math.pow(0.5, hours / (double) HALF_LIFE.toHours());
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
