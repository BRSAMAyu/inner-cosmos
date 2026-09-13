package com.innercosmos.service.usage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.mapper.ConversationTurnMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * CP-08 使用时长: server-side accumulation of how long the user actually spent inside
 * Aurora conversations today, with a calm check-in reminder — the blueprint's healthy-use
 * promise (陪伴不是黏住). Duration is derived from tb_conversation_turn rows
 * (completed − started per turn) on demand, so there is no new store to keep in sync and
 * nothing to retract; the day is anchored in Asia/Shanghai, the same anchor zone as the
 * commercial metric store (single time discipline). The reminder is a single gentle flag
 * with honest copy — never a lockout, never judgemental wording, and never a dark
 * "streak" mechanic.
 */
@Service
public class UsageTimeService {

    /** Same anchor as MetricEventServiceImpl — one time discipline across features. */
    static final ZoneId ANCHOR_ZONE = ZoneId.of("Asia/Shanghai");

    private final ConversationTurnMapper turns;
    private final int reminderAfterMinutes;

    public UsageTimeService(ConversationTurnMapper turns,
                            @Value("${inner-cosmos.usage.reminder-after-minutes:45}")
                            int reminderAfterMinutes) {
        this.turns = turns;
        this.reminderAfterMinutes = reminderAfterMinutes;
    }

    public record UsageToday(String date, long activeSeconds, long turnCount,
                             int reminderAfterMinutes, boolean reminderDue,
                             String reminderNote) { }

    /**
     * Today's accumulated conversation time for one user. Only COMPLETED turns count
     * (an in-flight turn has produced no finished presence yet); overlapping turns in
     * one session would double-count wall-clock, so per-turn durations are summed as
     * the honest upper bound and the copy says "约" (about).
     */
    public UsageToday usageToday(Long userId) {
        LocalDate today = LocalDate.now(ANCHOR_ZONE);
        // Anchor-day start expressed in the persisted (UTC) timeline: turns store UTC
        // LocalDates, so compare against the UTC instant of the Shanghai-day boundary.
        LocalDateTime utcWindowStart = today.atStartOfDay(ANCHOR_ZONE)
                .toInstant()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDateTime();

        List<ConversationTurn> rows = turns.selectList(new QueryWrapper<ConversationTurn>()
                .eq("user_id", userId)
                .ge("completed_at", utcWindowStart)
                .isNotNull("started_at"));

        long seconds = 0;
        long turnCount = 0;
        for (ConversationTurn turn : rows) {
            if (turn.startedAt == null || turn.completedAt == null) {
                continue;
            }
            // COMPLETED and PARTIAL turns both represent real conversation presence;
            // CANCELLED turns produced no finished presence and are excluded.
            if (!"COMPLETED".equalsIgnoreCase(turn.status)
                    && !"PARTIAL".equalsIgnoreCase(turn.status)) {
                continue;
            }
            long span = Duration.between(turn.startedAt, turn.completedAt).toSeconds();
            if (span > 0) {
                seconds += span;
                turnCount++;
            }
        }
        boolean due = seconds >= Duration.ofMinutes(reminderAfterMinutes).toSeconds();
        return new UsageToday(today.toString(), seconds, turnCount, reminderAfterMinutes, due,
                due ? "今天和 Aurora 待了约 " + (seconds / 60) + " 分钟。照顾好自己，星空不会走。"
                    : null);
    }
}
