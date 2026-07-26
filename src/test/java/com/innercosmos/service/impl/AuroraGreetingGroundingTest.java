package com.innercosmos.service.impl;

import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.entity.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuroraGreetingGroundingTest {

    @Test
    void greetingContextUsesProfileStateAndUnfinishedWorkWithoutInventingNullSignals() {
        UserProfile profile = new UserProfile();
        profile.bio = "Architecture, drawing and finding a sustainable rhythm abroad";
        AgentContext context = new AgentContext();
        context.themeSignals = List.of("Creative work", "Belonging", "Walking", "Extra ignored theme");
        context.environmentLabel = "Preparing a portfolio review";
        context.momentEmotionLabel = null;
        context.nearestTodo = "Bring the unfinished drawing to critique";
        context.activeTodos = List.of("Bring the unfinished drawing", "Send one portfolio page");
        context.dailyObservations = List.of("The riverside route has become grounding");

        Map<String, Object> grounding = AuroraAgentServiceImpl.greetingGrounding(profile, context);

        assertThat(grounding.get("profileInterests")).isEqualTo(context.themeSignals);
        assertThat(grounding.get("currentEnvironment")).isEqualTo("Preparing a portfolio review");
        assertThat(grounding.get("currentEmotion")).isEqualTo("");
        assertThat(grounding.get("nearestUnfinishedItem"))
                .isEqualTo("Bring the unfinished drawing to critique");
        assertThat(grounding.get("unfinishedItems")).isEqualTo(context.activeTodos);
    }

    @Test
    void providerUnavailableMessageIsNeutralAndNeverClaimsMemoryOrSentience() {
        StructuredAiResults.AuroraResult fallback =
                AuroraAgentServiceImpl.unavailableGreeting("en-SG");

        assertThat(fallback.segments).containsExactly(
                "AI-generated opening is unavailable right now. You can start with whatever matters to you.");
        assertThat(fallback.riskFlags).containsExactly("PROVIDER_UNAVAILABLE");
        assertThat(fallback.memoryReferenced).isFalse();
        assertThat(fallback.referencedMemoryIds).isEmpty();
        assertThat(fallback.segments.getFirst())
                .doesNotContain("remember", "thinking about you", "I missed");
    }
}
