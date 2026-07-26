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
        profile.auroraTone = "Clear and companionable";
        profile.proactiveSensitivity = 4;
        profile.reflectionDepth = 3;
        profile.currentEnvironmentLabel = "Preparing a portfolio review";
        AgentContext context = new AgentContext();
        context.themeSignals = List.of("Creative work", "Belonging", "Walking", "Extra ignored theme");
        context.environmentLabel = "Preparing a portfolio review";
        context.momentEmotionLabel = null;
        context.nearestTodo = "Bring the unfinished drawing to critique";
        context.activeTodos = List.of("Bring the unfinished drawing", "Send one portfolio page");
        context.dailyObservations = List.of("The riverside route has become grounding");

        Map<String, Object> grounding =
                AuroraAgentServiceImpl.greetingGrounding(profile, context, "Mira");

        assertThat(grounding).containsEntry("userNickname", "Mira")
                .containsEntry("auroraTone", "Clear and companionable")
                .containsEntry("proactiveSensitivity", 4)
                .containsEntry("reflectionDepth", 3);
        assertThat(grounding.get("profileInterests")).isEqualTo(context.themeSignals);
        assertThat(grounding.get("currentEnvironment")).isEqualTo("Preparing a portfolio review");
        assertThat(grounding.get("currentEmotion")).isEqualTo("");
        assertThat(grounding.get("nearestUnfinishedItem"))
                .isEqualTo("Bring the unfinished drawing to critique");
        assertThat(grounding.get("unfinishedItems")).isEqualTo(context.activeTodos);
    }

    @Test
    void chineseAndEnglishQuickHelloProfilesProduceDistinctCompleteGrounding() {
        UserProfile chinese = new UserProfile();
        chinese.auroraTone = "温柔安静";
        chinese.proactiveSensitivity = 1;
        chinese.reflectionDepth = 2;
        chinese.currentEnvironmentLabel = "关系 · 刚转到新的小组，还不知道怎么自然加入";

        UserProfile english = new UserProfile();
        english.auroraTone = "朋友式直接";
        english.proactiveSensitivity = 5;
        english.reflectionDepth = 4;
        english.currentEnvironmentLabel = "Making and study · preparing a live systems demo";

        Map<String, Object> zh = AuroraAgentServiceImpl.greetingGrounding(
                chinese, new AgentContext(), "小岚");
        Map<String, Object> en = AuroraAgentServiceImpl.greetingGrounding(
                english, new AgentContext(), "Noah");

        assertThat(zh).containsEntry("userNickname", "小岚")
                .containsEntry("auroraTone", "温柔安静")
                .containsEntry("proactiveSensitivity", 1)
                .containsEntry("reflectionDepth", 2)
                .containsEntry("currentEnvironment", chinese.currentEnvironmentLabel);
        assertThat(en).containsEntry("userNickname", "Noah")
                .containsEntry("auroraTone", "朋友式直接")
                .containsEntry("proactiveSensitivity", 5)
                .containsEntry("reflectionDepth", 4)
                .containsEntry("currentEnvironment", english.currentEnvironmentLabel);
        assertThat(zh).isNotEqualTo(en);
    }

    @Test
    void regularTurnProfileBriefCarriesTheSameNicknameAndQuickHelloCalibration() {
        UserProfile profile = new UserProfile();
        profile.auroraName = "Aurora";
        profile.auroraTone = "理性清晰";
        profile.proactiveSensitivity = 4;
        profile.reflectionDepth = 3;
        profile.currentEnvironmentLabel = "变化 / 选择 · deciding whether to change course";
        profile.allowMemoryRecall = true;

        String brief = AuroraAgentServiceImpl.profileBrief(profile, "River");

        assertThat(brief)
                .contains("userNickname=River")
                .contains("responseTone=理性清晰")
                .contains("proactiveSensitivity=4")
                .contains("reflectionDepth=3")
                .contains("currentEnvironment=变化 / 选择 · deciding whether to change course");
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

    @Test
    void providerUnavailableChineseMessageAlsoFailsClosedWithoutPersonalisedTemplate() {
        StructuredAiResults.AuroraResult fallback =
                AuroraAgentServiceImpl.unavailableGreeting("zh-CN");

        assertThat(fallback.segments).containsExactly(
                "AI 生成的开场暂时不可用。你可以从此刻最想说的事情开始。");
        assertThat(fallback.riskFlags).containsExactly("PROVIDER_UNAVAILABLE");
        assertThat(fallback.memoryReferenced).isFalse();
        assertThat(fallback.referencedMemoryIds).isEmpty();
        assertThat(fallback.segments.getFirst())
                .doesNotContain("记得你", "想你", "小岚", "Noah");
    }
}
