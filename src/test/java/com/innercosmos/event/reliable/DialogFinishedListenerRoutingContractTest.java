package com.innercosmos.event.reliable;

import com.innercosmos.event.CapsuleSuggestionListener;
import com.innercosmos.event.ClaimCandidateExtractListener;
import com.innercosmos.event.EmotionTraceListener;
import com.innercosmos.event.GravityRecalculateListener;
import com.innercosmos.event.MemoryExtractListener;
import com.innercosmos.event.TodoExtractListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DialogFinishedListenerRoutingContractTest {
    @Test
    void processLocalListenersAreDisabledWhenDurableOutboxIsEnabled() {
        for (Class<?> listener : List.of(MemoryExtractListener.class, EmotionTraceListener.class,
                TodoExtractListener.class, ClaimCandidateExtractListener.class,
                GravityRecalculateListener.class, CapsuleSuggestionListener.class)) {
            ConditionalOnProperty condition = listener.getAnnotation(ConditionalOnProperty.class);
            assertThat(condition).as(listener.getSimpleName()).isNotNull();
            assertThat(condition.name()).containsExactly("inner-cosmos.events.outbox.enabled");
            assertThat(condition.havingValue()).isEqualTo("false");
            assertThat(condition.matchIfMissing()).isTrue();
        }
    }
}
