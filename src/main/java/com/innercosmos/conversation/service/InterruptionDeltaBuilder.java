package com.innercosmos.conversation.service;

import com.innercosmos.conversation.entity.MessageBubble;
import com.innercosmos.conversation.vo.InterruptionDeltaVO;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Deterministically projects durable delivery facts into the user-safe interruption contract. */
@Component
public class InterruptionDeltaBuilder {
    private static final int MAX_ITEM_CHARS = 600;
    private static final int MAX_SUMMARY_CHARS = 900;
    private static final int MAX_NEW_MESSAGE_CHARS = 1_200;

    public InterruptionDeltaVO build(TurnTimelineVO timeline, String newUserMessage) {
        InterruptionDeltaVO delta = new InterruptionDeltaVO();
        if (timeline == null) return delta;

        List<String> delivered = new ArrayList<>();
        List<String> cancelledPurposes = new ArrayList<>();
        for (MessageBubble bubble : safe(timeline.bubbles)) {
            String visible = deliveredPart(bubble);
            if (!visible.isBlank()) delivered.add(bounded(visible, MAX_ITEM_CHARS));
            if ("CANCELLED".equals(bubble.status) && bubble.purpose != null && !bubble.purpose.isBlank()) {
                cancelledPurposes.add(bounded(bubble.purpose, 120));
            }
        }
        delta.deliveredContent = List.copyOf(delivered);
        delta.deliveredSummary = bounded(String.join(" / ", delivered), MAX_SUMMARY_CHARS);
        delta.cancelledBubblePurposes = cancelledPurposes.stream().distinct().toList();
        delta.newUserMessage = bounded(newUserMessage, MAX_NEW_MESSAGE_CHARS);
        delta.continuityDecision = decision(delivered, delta.newUserMessage);
        delta.changedFacts = delta.newUserMessage.isBlank()
                ? List.of()
                : List.of("用户在打断后补充：" + delta.newUserMessage);
        delta.mustNotRepeat = delta.deliveredSummary.isBlank()
                ? List.of()
                : List.of("不要逐字重复已送达内容：" + delta.deliveredSummary);
        int priorRevision = safe(timeline.deliberations).stream()
                .filter(snapshot -> snapshot.planRevision != null)
                .mapToInt(snapshot -> snapshot.planRevision)
                .max().orElse(timeline.activePlan == null || timeline.activePlan.planVersion == null
                        ? 0 : timeline.activePlan.planVersion);
        delta.planRevision = priorRevision + 1;
        return delta;
    }

    private static String decision(List<String> delivered, String newMessage) {
        if (newMessage == null || newMessage.isBlank()) return "PARK";
        return delivered.isEmpty() ? "SWITCH" : "MERGE";
    }

    private static String deliveredPart(MessageBubble bubble) {
        if (bubble == null || bubble.content == null) return "";
        if ("COMMITTED".equals(bubble.status)) return bubble.content;
        int deliveredChars = Math.max(0, Math.min(
                bubble.deliveredChars == null ? 0 : bubble.deliveredChars, bubble.content.length()));
        return bubble.content.substring(0, deliveredChars);
    }

    private static String bounded(String value, int maxChars) {
        if (value == null || value.isBlank()) return "";
        String clean = value.strip();
        return clean.length() <= maxChars ? clean : clean.substring(0, maxChars) + "…";
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
