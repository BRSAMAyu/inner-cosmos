package com.innercosmos.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuroraGeminiStageDefaultsTest {

    @Test
    void classroomStageDefaultsStayInsideGeminiFamilyAndKeepSpeakerFast() {
        LlmConfig.AuroraStageProperties stages = new LlmConfig.AuroraStageProperties();

        assertThat(stages.fastModel).isEqualTo("gemini-3.6-flash");
        assertThat(stages.speakerModel).isEqualTo("gemini-3.6-flash");
        assertThat(stages.thinkerModel).isEqualTo("gemini-3.6-flash");
        assertThat(stages.speakerThinkingLevel).isEqualTo("minimal");
        assertThat(stages.speakerMaxTokens).isEqualTo(2_048);
    }
}
