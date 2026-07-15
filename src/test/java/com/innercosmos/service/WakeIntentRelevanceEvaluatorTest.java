package com.innercosmos.service;

import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.mapper.DialogMessageMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WakeIntentRelevanceEvaluatorTest {
    private final DialogMessageMapper messages = mock(DialogMessageMapper.class);
    private final WakeIntentRelevanceEvaluator evaluator = new WakeIntentRelevanceEvaluator(messages);

    @Test
    void newerExplicitResolutionSupersedesDeliveryButOrdinaryContinuationDoesNot() {
        WakeIntent intent = new WakeIntent();
        intent.userId = 7L;
        intent.contextSessionId = 8L;
        intent.contextMessageId = 9L;
        DialogMessage resolved = new DialogMessage();
        resolved.textContent = "刚刚已经解决了，不用再提醒";
        when(messages.selectList(any())).thenReturn(List.of(resolved));

        assertThat(evaluator.evaluate(intent).relevant()).isFalse();
        assertThat(evaluator.evaluate(intent).reason()).isEqualTo("context_resolved_by_new_user_message");

        resolved.textContent = "我又想到一个相关细节";
        assertThat(evaluator.evaluate(intent).relevant()).isTrue();
    }
}
