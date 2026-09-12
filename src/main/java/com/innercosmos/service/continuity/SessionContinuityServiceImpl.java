package com.innercosmos.service.continuity;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.DialogSummary;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.DialogSummaryMapper;
import com.innercosmos.service.continuity.SessionContinuityService.CarryNote;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class SessionContinuityServiceImpl implements SessionContinuityService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);
    /** Continuity is capped at 30 days: older silence is honored, not resurrected. */
    private static final int CONTINUITY_WINDOW_DAYS = 30;

    private final DialogSessionMapper sessionMapper;
    private final DialogSummaryMapper summaryMapper;

    public SessionContinuityServiceImpl(DialogSessionMapper sessionMapper,
                                        DialogSummaryMapper summaryMapper) {
        this.sessionMapper = sessionMapper;
        this.summaryMapper = summaryMapper;
    }

    @Override
    public OpeningContext openingContext(Long userId) {
        DialogSession prior = sessionMapper.selectOne(new QueryWrapper<DialogSession>()
                .eq("user_id", userId).isNotNull("ended_at")
                .orderByDesc("ended_at").last("LIMIT 1"));
        if (prior == null || prior.endedAt == null
                || prior.endedAt.isBefore(LocalDateTime.now().minusDays(CONTINUITY_WINDOW_DAYS))) {
            // First conversation (or a silence longer than the window): honest fresh start,
            // zero fabricated references — no "记得你说过" without a source.
            return new OpeningContext(false, null, null, List.of(),
                    "我们从头开始。你想说的那件事，慢慢来。");
        }

        List<CarryNote> notes = new ArrayList<>();
        DialogSummary summary = summaryMapper.selectOne(
                new QueryWrapper<DialogSummary>().eq("session_id", prior.id)
                        .orderByDesc("id").last("LIMIT 1"));
        if (summary != null && summary.summaryText != null && !summary.summaryText.isBlank()) {
            String excerpt = summary.summaryText.strip();
            if (excerpt.length() > 120) {
                excerpt = excerpt.substring(0, 120) + "…";
            }
            notes.add(new CarryNote("PRIOR_SUMMARY", excerpt,
                    "上次对话（" + prior.endedAt.atZone(SHANGHAI).format(DAY_FORMAT) + "）的整理"));
        }
        if (summary != null && summary.keyTopics != null && !summary.keyTopics.isBlank()) {
            notes.add(new CarryNote("PRIOR_TOPICS", summary.keyTopics.strip(),
                    "上次对话记录的主题"));
        }

        String day = prior.endedAt.atZone(SHANGHAI).format(DAY_FORMAT);
        String opening = notes.isEmpty()
                ? "你" + day + "来过，那次没有留下整理。今天想从哪里开始？"
                : "你" + day + "聊过一次，我带着那次留下的整理在这里。想继续，也可以从新的开始。";
        return new OpeningContext(true, prior.id,
                prior.endedAt.atZone(SHANGHAI).toString(), notes, opening);
    }
}
