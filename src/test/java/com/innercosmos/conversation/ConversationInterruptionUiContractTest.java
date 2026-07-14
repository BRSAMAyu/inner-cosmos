package com.innercosmos.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ConversationInterruptionUiContractTest {
    @Test
    void auroraChatExposesStopAndTreatsNewMessageAsNormalInterruption() throws IOException {
        String html = new ClassPathResource("static/pages/aurora-chat.html")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"stopTurnBtn\"")
                .contains("/api/aurora/turns/")
                .contains("/stop")
                .contains("turn.interrupted")
                .contains("await stopCurrentTurn()")
                .contains("你可以直接接着说");
    }
}
