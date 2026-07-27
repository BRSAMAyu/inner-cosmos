package com.innercosmos.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.innercosmos.ai.claim.ClaimAuthority;
import com.innercosmos.ai.claim.ClaimCandidate;
import com.innercosmos.ai.claim.ClaimExtractionResult;
import com.innercosmos.ai.claim.ClaimTypes;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.DialogMessage;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ClaimExtractionServiceImplTest {

    private DialogMessage user(long id, String text) {
        DialogMessage m = new DialogMessage();
        m.id = id;
        m.userId = 5L;
        m.speaker = "USER";
        m.textContent = text;
        return m;
    }

    private DialogMessage aurora(long id, String text) {
        DialogMessage m = user(id, text);
        m.speaker = "AURORA";
        return m;
    }

    @Test
    void mockModeFallsBackToDeterministicExtractor() {
        StructuredAiService structured = mock(StructuredAiService.class);
        // Simulate mock/no-provider mode: the harness returns the deterministic fallback.
        when(structured.call(eq(5L), any(), any(), any(), eq(ClaimExtractionResult.class), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(5)).get());
        ClaimExtractionServiceImpl service = new ClaimExtractionServiceImpl(structured);

        List<ClaimCandidate> out = service.extract(5L, List.of(
                user(1L, "我特别喜欢在下雨天读书"),
                user(2L, "我是不是太敏感了？")));

        assertThat(out).extracting(ClaimCandidate::claimType)
                .containsExactly(ClaimTypes.PREFERENCE);
        ClaimCandidate preference = out.stream()
                .filter(candidate -> ClaimTypes.PREFERENCE.equals(candidate.claimType())).findFirst().orElseThrow();
        assertThat(preference.value()).contains("读书");
        assertThat(preference.provenanceMessageIds()).containsExactly(1L);
    }

    @Test
    void expressionStyleRequiresSixDistinctEvidenceMessages() {
        StructuredAiService structured = mock(StructuredAiService.class);
        when(structured.call(eq(5L), any(), any(), any(), eq(ClaimExtractionResult.class), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(5)).get());
        ClaimExtractionServiceImpl service = new ClaimExtractionServiceImpl(structured);

        List<ClaimCandidate> out = service.extract(5L, List.of(
                user(1L, "a"), user(2L, "b"), user(3L, "c"),
                user(4L, "d"), user(5L, "e"), user(6L, "f")));

        ClaimCandidate style = out.stream()
                .filter(candidate -> ClaimTypes.EXPRESSION_STYLE.equals(candidate.claimType()))
                .findFirst().orElseThrow();
        assertThat(style.confidence()).isGreaterThanOrEqualTo(0.8);
        assertThat(style.provenanceMessageIds()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void sanitizeRejectsProviderStyleGuessFromSparseEvidence() {
        StructuredAiService structured = mock(StructuredAiService.class);
        ClaimExtractionResult sparseGuess = new ClaimExtractionResult(List.of(
                new ClaimCandidate(ClaimTypes.EXPRESSION_STYLE, "style:short", "brief",
                        ClaimAuthority.MODEL_INFERENCE, 0.99, List.of(1L, 2L), "length", false)));
        when(structured.call(eq(5L), any(), any(), any(), eq(ClaimExtractionResult.class), any()))
                .thenReturn(sparseGuess);
        ClaimExtractionServiceImpl service = new ClaimExtractionServiceImpl(structured);

        List<ClaimCandidate> out = service.extract(5L, List.of(user(1L, "a"), user(2L, "b")));

        assertThat(out).isEmpty();
    }

    @Test
    void sanitizeRejectsClaimsWhoseOnlyEvidenceWasWrittenByAurora() {
        StructuredAiService structured = mock(StructuredAiService.class);
        ClaimExtractionResult reflectedGuess = new ClaimExtractionResult(List.of(
                new ClaimCandidate(ClaimTypes.FACT, "fact:calm", "calm",
                        ClaimAuthority.MODEL_INFERENCE, 0.9, List.of(2L), "Aurora said it", false)));
        when(structured.call(eq(5L), any(), any(), any(), eq(ClaimExtractionResult.class), any()))
                .thenReturn(reflectedGuess);
        ClaimExtractionServiceImpl service = new ClaimExtractionServiceImpl(structured);

        List<ClaimCandidate> out = service.extract(5L, List.of(
                user(1L, "I am unsure"), aurora(2L, "You are a calm person")));

        assertThat(out).isEmpty();
    }

    @Test
    void sanitizeDropsUnknownTypeAndForeignProvenance() {
        StructuredAiService structured = mock(StructuredAiService.class);
        // Simulate a real Provider that hallucinated: an unknown type, a claim whose provenance
        // points at a message the user never sent, and one valid claim.
        ClaimExtractionResult hallucinated = new ClaimExtractionResult(List.of(
                new ClaimCandidate("HOROSCOPE", "x", "天蝎座", ClaimAuthority.MODEL_INFERENCE, 0.9, List.of(1L), "e", false),
                new ClaimCandidate(ClaimTypes.FACT, "y", "住在火星", ClaimAuthority.SINGLE_EXPLICIT, 0.9, List.of(999L), "e", false),
                new ClaimCandidate(ClaimTypes.VALUE, "z", "诚实", ClaimAuthority.SINGLE_EXPLICIT, 1.7, List.of(1L), "e", false)));
        when(structured.call(eq(5L), any(), any(), any(), eq(ClaimExtractionResult.class), any()))
                .thenReturn(hallucinated);
        ClaimExtractionServiceImpl service = new ClaimExtractionServiceImpl(structured);

        List<ClaimCandidate> out = service.extract(5L, List.of(user(1L, "我觉得诚实特别重要")));

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().claimType()).isEqualTo(ClaimTypes.VALUE);
        assertThat(out.getFirst().value()).isEqualTo("诚实");
        assertThat(out.getFirst().confidence()).isEqualTo(1.0); // clamped from 1.7
        assertThat(out.getFirst().provenanceMessageIds()).containsExactly(1L);
    }
}
