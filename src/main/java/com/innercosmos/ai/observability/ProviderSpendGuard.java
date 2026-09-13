package com.innercosmos.ai.observability;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CP-40 cost guardrail: a per-user, per-day provider spend budget enforced BEFORE the
 * provider is called. Request rate limiting (ApiRateLimitFilter) bounds request frequency;
 * this bounds what those requests COST — one Aurora dual-kernel turn is several provider
 * calls, so a per-request limiter alone cannot cap daily spend. When the daily call or
 * estimated-token budget is exhausted the call fails fast with a dedicated error code and
 * a clear, non-alarming user message; local features are untouched.
 *
 * <p>Counters are per-pod in-memory (Asia/Shanghai day anchor, matching the metric
 * pipeline). The Redis-backed multi-pod version is deliberately NOT improvised here — it
 * belongs with CP-38's workload/role isolation and CP-37's real environment, and until
 * then a per-pod cap is an under-approximation of the fleet limit, never an over-estimate
 * that silently lets users exceed the intended budget on a single-pod deployment.
 */
@Component
public class ProviderSpendGuard {

    @Value("${inner-cosmos.ai.spend.enabled:true}")
    private boolean enabled = true;
    @Value("${inner-cosmos.ai.spend.daily-user-call-budget:400}")
    private long dailyCallBudget = 400;
    @Value("${inner-cosmos.ai.spend.daily-user-token-budget:400000}")
    private long dailyTokenBudget = 400_000;

    private final ConcurrentHashMap<Long, DayCounters> counters = new ConcurrentHashMap<>();
    private volatile Clock clock = Clock.systemDefaultZone();
    private volatile MeterRegistry meterRegistry;

    /** Test hook: fixed clock for day-rollover semantics. */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Autowired(required = false)
    void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    record DayCounters(LocalDate day, AtomicLong calls, AtomicLong tokens) {}

    /**
     * Throws {@link BusinessException} with {@link ErrorCode#AI_SPEND_EXCEEDED} when the user's
     * daily budget is already spent. Must run before the provider call so an exhausted budget
     * costs nothing further.
     */
    public void tryAcquire(Long userId, String moduleName) {
        if (!enabled || userId == null) return;
        DayCounters day = today(userId);
        long calls = day.calls().get();
        long tokens = day.tokens().get();
        String dimension = null;
        long used = 0;
        long budget = 0;
        if (calls >= dailyCallBudget) {
            dimension = "calls";
            used = calls;
            budget = dailyCallBudget;
        } else if (tokens >= dailyTokenBudget) {
            dimension = "tokens";
            used = tokens;
            budget = dailyTokenBudget;
        }
        if (dimension != null) {
            metric(userId, moduleName, "denied", dimension, 0);
            throw new BusinessException(ErrorCode.AI_SPEND_EXCEEDED,
                    "今天的 AI 用量已达到每日上限（" + dimension + " " + used + "/" + budget
                            + "），本地功能不受影响，明天会自动恢复。");
        }
        metric(userId, moduleName, "allowed", null, 0);
    }

    /** Records an actually-spent provider call with its estimated token cost. */
    public void record(Long userId, String moduleName, long estimatedTokens) {
        if (!enabled || userId == null) return;
        DayCounters day = today(userId);
        day.calls().incrementAndGet();
        day.tokens().addAndGet(Math.max(0, estimatedTokens));
        metric(userId, moduleName, "recorded", null, Math.max(0, estimatedTokens));
    }

    /** Introspection for operators/tests: today's counters for a user. */
    public DayCounters snapshot(Long userId) {
        DayCounters current = counters.get(userId);
        return current == null ? new DayCounters(LocalDate.now(clock), new AtomicLong(), new AtomicLong())
                : current;
    }

    /**
     * CP-17 governance tests / ops introspection: provider calls actually recorded today for a
     * user. The {@link DayCounters} record stays package-private; this is the public read of the
     * one dimension the gateway negative tests assert (a refused/interrupted call records none).
     */
    public long recordedCallsToday(Long userId) {
        return snapshot(userId).calls().get();
    }

    /** CP-46 quota display view: today's usage against both budgets plus the reset
     *  instant (next start-of-day in the guard's own clock zone — the same anchor the
     *  day counters roll on). 配额显示剩余与重置时间. */
    public record DailyQuota(long usedCalls, long callBudget, long usedTokens,
                             long tokenBudget, java.time.LocalDateTime resetsAtUtc) {
        public long remainingCalls() {
            return Math.max(0, callBudget - usedCalls);
        }
    }

    public DailyQuota dailyQuota(Long userId) {
        DayCounters current = snapshot(userId);
        java.time.ZonedDateTime reset = LocalDate.now(clock).plusDays(1)
                .atStartOfDay(clock.getZone());
        return new DailyQuota(current.calls().get(), dailyCallBudget,
                current.tokens().get(), dailyTokenBudget,
                reset.withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime());
    }

    private DayCounters today(Long userId) {
        LocalDate today = LocalDate.now(clock);
        DayCounters day = counters.compute(userId, (id, current) ->
                current == null || !current.day().equals(today)
                        ? new DayCounters(today, new AtomicLong(), new AtomicLong())
                        : current);
        // Opportunistic prune: drop other users' stale days so the map stays bounded by
        // TODAY's active users, not by lifetime users.
        if (counters.size() > 1024) {
            counters.values().removeIf(entry -> !entry.day().equals(today));
        }
        return day;
    }

    private void metric(Long userId, String moduleName, String outcome, String dimension, long tokens) {
        MeterRegistry registry = meterRegistry;
        if (registry == null) return;
        registry.counter("ai.spend.decisions", "outcome", outcome,
                "dimension", dimension == null ? "none" : dimension).increment();
        if (tokens > 0) {
            registry.counter("ai.spend.tokens", "module", moduleName == null ? "unknown" : moduleName)
                    .increment(tokens);
        }
        Timer.builder("ai.spend.decisions.latency").tag("outcome", outcome)
                .register(registry).record(Duration.ZERO);
    }
}
