package com.innercosmos.ai.runtime;

import com.innercosmos.ai.client.MockLlmClient;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the hard product requirement from `InnerVoiceComposer`'s javadoc: the composed inner-voice
 * line must be genuinely DISTINCT from the visible spoken reply -- never a restatement, paraphrase
 * or summary of it -- even though it is deliberately grounded in the same planner signals
 * (emotionalNeed / relationshipMove) that produced the spoken reply.
 *
 * <p>Exercises the real {@link MockLlmClient} (not a hand-rolled fake) so this test also pins the
 * mock provider's own deterministic "AURORA_INNER_VOICE_*" dispatch branch.
 */
class InnerVoiceComposerTest {

    @Test
    void composedLineIsGroundedButNotARestatementOfTheSpokenReply() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("MOCK");
        LlmConfig config = new LlmConfig();
        config.mode = "dev";
        MockLlmClient mockClient = new MockLlmClient(Runnable::run);
        StructuredAiService structured = new StructuredAiService(mockClient, ab, config);
        InnerVoiceComposer composer = new InnerVoiceComposer(structured);

        var plan = new StructuredAiResults.AuroraPlanResult();
        plan.emotionalNeed = "先承认此刻的压力，不急着解释或推动";
        plan.relationshipMove = "保持连续，把下一步选择权交还用户";

        var spoken = new StructuredAiResults.AuroraResult();
        spoken.segments = List.of("我知道你现在压力很大，我们可以先深呼吸一下，一步一步来，不用着急。");

        String innerVoice = composer.compose(7L, "DAILY_TALK", plan, spoken, mockClient);

        assertThat(innerVoice).isNotNull().isNotBlank();
        assertThat(innerVoice.codePointCount(0, innerVoice.length()))
            .as("inner voice line must stay within the <=40 Chinese character budget")
            .isLessThanOrEqualTo(40);

        // Hard requirement: never a verbatim/near-verbatim restatement of the spoken reply.
        for (String segment : spoken.segments) {
            assertThat(segment).doesNotContain(innerVoice);
            assertThat(innerVoice).doesNotContain(segment);
        }
        double overlap = charBigramOverlapRatio(innerVoice, String.join("", spoken.segments));
        assertThat(overlap)
            .as("inner voice must read as genuinely distinct interior speech, not a paraphrase of the spoken reply (bigram overlap=%s)", overlap)
            .isLessThan(0.3);
    }

    /**
     * Fraction of the candidate's character bigrams that also occur in the reference text --
     * a simple, language-agnostic proxy for "these two texts share most of their wording",
     * appropriate for CJK text where word-boundary tokenization is unreliable.
     */
    private static double charBigramOverlapRatio(String candidate, String reference) {
        Set<String> candidateBigrams = bigrams(candidate);
        Set<String> referenceBigrams = bigrams(reference);
        if (candidateBigrams.isEmpty()) return 0.0;
        long shared = candidateBigrams.stream().filter(referenceBigrams::contains).count();
        return (double) shared / candidateBigrams.size();
    }

    private static Set<String> bigrams(String text) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 1 < text.length(); i++) {
            result.add(text.substring(i, i + 2));
        }
        return result;
    }
}
