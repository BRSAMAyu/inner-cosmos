package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.mapper.DialogMessageMapper;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/** Rechecks explicit resolution evidence after the agreement anchor, immediately before delivery. */
@Component
public class WakeIntentRelevanceEvaluator {
    private static final Pattern RESOLVED = Pattern.compile(
        "(已经|已|刚刚)?(解决|完成|搞定|处理好)了?|不用(再)?提醒|别再提醒|no longer need|resolved|done",
        Pattern.CASE_INSENSITIVE);
    private final DialogMessageMapper messages;

    public WakeIntentRelevanceEvaluator(DialogMessageMapper messages) {
        this.messages = messages;
    }

    public Decision evaluate(WakeIntent intent) {
        if (intent.contextSessionId == null || intent.contextMessageId == null) return Decision.keep();
        var newer = messages.selectList(new QueryWrapper<DialogMessage>()
            .eq("session_id", intent.contextSessionId).eq("user_id", intent.userId).eq("speaker", "USER")
            .gt("id", intent.contextMessageId).orderByDesc("id").last("LIMIT 20"));
        boolean resolved = newer.stream().map(row -> row.textContent == null ? "" : row.textContent.toLowerCase(Locale.ROOT))
            .anyMatch(text -> RESOLVED.matcher(text).find());
        return resolved ? new Decision(false, "context_resolved_by_new_user_message") : Decision.keep();
    }

    public record Decision(boolean relevant, String reason) {
        public static Decision keep() { return new Decision(true, "still_relevant"); }
    }
}
