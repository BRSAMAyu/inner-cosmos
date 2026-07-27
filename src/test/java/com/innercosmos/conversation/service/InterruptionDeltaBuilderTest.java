package com.innercosmos.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.conversation.entity.MessageBubble;
import com.innercosmos.conversation.entity.TurnDeliberationSnapshot;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterruptionDeltaBuilderTest {
    private final InterruptionDeltaBuilder builder = new InterruptionDeltaBuilder();

    @Test
    void exposesOnlyDeliveredPrefixAndPurposeOfCancelledRemainder() throws Exception {
        MessageBubble committed = bubble("ACKNOWLEDGE", "我听见你很难受", "COMMITTED", null);
        MessageBubble partial = bubble("DEEPEN", "已经送达|绝不能泄漏的旧计划", "CANCELLED", 4);
        MessageBubble unsent = bubble("GENTLE_NEXT_STEP", "未发送的建议绝不能成为共同经历", "CANCELLED", 0);
        TurnDeliberationSnapshot revision = new TurnDeliberationSnapshot();
        revision.planRevision = 3;
        TurnTimelineVO timeline = new TurnTimelineVO();
        timeline.bubbles = List.of(committed, partial, unsent);
        timeline.deliberations = List.of(revision);

        var delta = builder.build(timeline, "等等，我现在只想安静一下");
        String json = new ObjectMapper().writeValueAsString(delta);

        assertThat(delta.deliveredContent).containsExactly("我听见你很难受", "已经送达");
        assertThat(delta.deliveredSummary).contains("我听见你很难受", "已经送达");
        assertThat(delta.cancelledBubblePurposes).containsExactly("DEEPEN", "GENTLE_NEXT_STEP");
        assertThat(delta.newUserMessage).isEqualTo("等等，我现在只想安静一下");
        assertThat(delta.continuityDecision).isEqualTo("MERGE");
        assertThat(delta.changedFacts).containsExactly("用户在打断后补充：等等，我现在只想安静一下");
        assertThat(delta.mustNotRepeat).singleElement().asString().contains("已送达");
        assertThat(delta.planRevision).isEqualTo(4);
        assertThat(json).doesNotContain("绝不能泄漏的旧计划", "未发送的建议绝不能成为共同经历");
    }

    @Test
    void cancellationBeforeDeliveryLeaksZeroOldPlanTextAndSwitchesToNewMessage() throws Exception {
        TurnTimelineVO timeline = new TurnTimelineVO();
        timeline.bubbles = List.of(
                bubble("ACKNOWLEDGE", "旧计划第一句", "CANCELLED", 0),
                bubble("GENTLE_NEXT_STEP", "旧计划第二句", "CANCELLED", null));

        var delta = builder.build(timeline, "我们换个话题");
        String json = new ObjectMapper().writeValueAsString(delta);

        assertThat(delta.deliveredContent).isEmpty();
        assertThat(delta.deliveredSummary).isEmpty();
        assertThat(delta.mustNotRepeat).isEmpty();
        assertThat(delta.cancelledBubblePurposes).containsExactly("ACKNOWLEDGE", "GENTLE_NEXT_STEP");
        assertThat(delta.continuityDecision).isEqualTo("SWITCH");
        assertThat(delta.planRevision).isEqualTo(1);
        assertThat(json).doesNotContain("旧计划第一句", "旧计划第二句");
    }

    private static MessageBubble bubble(String purpose, String content, String status,
                                        Integer deliveredChars) {
        MessageBubble bubble = new MessageBubble();
        bubble.purpose = purpose;
        bubble.content = content;
        bubble.status = status;
        bubble.deliveredChars = deliveredChars;
        return bubble;
    }
}
