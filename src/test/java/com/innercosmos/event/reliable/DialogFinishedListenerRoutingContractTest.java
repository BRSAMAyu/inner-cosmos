package com.innercosmos.event.reliable;

import com.innercosmos.event.CapsuleSuggestionListener;
import com.innercosmos.event.ClaimCandidateExtractListener;
import com.innercosmos.event.EmotionTraceListener;
import com.innercosmos.event.MemoryExtractListener;
import com.innercosmos.event.TodoExtractListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DialogFinishedListenerRoutingContractTest {
    @Test
    void processLocalListenersAreDisabledWhenDurableOutboxIsEnabled() {
        // Gemini audit 1.6 (CONFIRMED/P1): GravityRecalculateListener no longer exists as an
        // independent listener -- it used to race MemoryExtractListener on the same event with no
        // ordering guarantee. Its recompute logic (GravityRecalculationService) is now called
        // sequentially, AFTER extraction, from inside MemoryExtractListener itself (this path) and
        // from DialogFinishedProjectionHandler (the durable-outbox path) -- it is no longer a
        // separate routed listener with its own on/off contract to check here.
        for (Class<?> listener : List.of(MemoryExtractListener.class, EmotionTraceListener.class,
                TodoExtractListener.class, ClaimCandidateExtractListener.class,
                CapsuleSuggestionListener.class)) {
            ConditionalOnProperty condition = listener.getAnnotation(ConditionalOnProperty.class);
            assertThat(condition).as(listener.getSimpleName()).isNotNull();
            assertThat(condition.name()).containsExactly("inner-cosmos.events.outbox.enabled");
            assertThat(condition.havingValue()).isEqualTo("false");
            assertThat(condition.matchIfMissing()).isTrue();
        }
    }
}
