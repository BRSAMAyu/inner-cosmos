package com.innercosmos.service.impl;

import com.innercosmos.ai.claim.ClaimCandidate;
import com.innercosmos.ai.claim.ClaimCandidateExtractor;
import com.innercosmos.ai.claim.ClaimExtractionResult;
import com.innercosmos.ai.claim.ClaimTypes;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.service.ClaimExtractionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Wraps the deterministic {@link ClaimCandidateExtractor} with the mock-safe structured-AI harness:
 * a real Provider is attempted when configured, and the deterministic engine is the fallback (this
 * is what runs in mock/test mode). Every candidate — provider or deterministic — is sanitized to the
 * precision-first invariants so an unreliable Provider can never fabricate a claim, mislabel its
 * type, or attach it to a message the user did not send.
 */
@Service
public class ClaimExtractionServiceImpl implements ClaimExtractionService {

    private static final String MODULE = "claim_extraction";
    private static final String INSTRUCTION = """
            Extract stable user-model claims the user states about THEMSELVES from their own messages.
            Only include explicit first-person self-statements. Do NOT infer from questions,
            hypotheticals, other people's words, or momentary one-off feelings. For each claim return
            claimType (one of FACT, PREFERENCE, VALUE, RELATION, EMOTION_PATTERN, HABIT,
            EXPRESSION_STYLE, NEED, BOUNDARY, TREND, UNCERTAINTY), a concise value, an authorityLevel
            (SINGLE_EXPLICIT/REPEATED_EXPLICIT/REPEATED_BEHAVIOR/MODEL_INFERENCE), a confidence in
            0..1, the provenanceMessageIds it is evidenced by, the matched evidenceText, and whether
            the user themselves was uncertain. Prefer precision over recall.
            """.trim();

    private final StructuredAiService structuredAiService;

    public ClaimExtractionServiceImpl(StructuredAiService structuredAiService) {
        this.structuredAiService = structuredAiService;
    }

    @Override
    public List<ClaimCandidate> extract(Long userId, List<DialogMessage> messages) {
        if (messages == null || messages.isEmpty()) return List.of();
        Set<Long> ownedMessageIds = messages.stream()
                .filter(m -> m != null && m.id != null)
                .map(m -> m.id).collect(Collectors.toSet());
        if (ownedMessageIds.isEmpty()) return List.of();

        Map<String, Object> context = new LinkedHashMap<>();
        List<Map<String, Object>> turns = new ArrayList<>();
        for (DialogMessage m : messages) {
            if (m == null || m.id == null) continue;
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("id", m.id);
            turn.put("speaker", m.speaker);
            turn.put("text", m.textContent);
            turns.add(turn);
        }
        context.put("messages", turns);

        ClaimExtractionResult result = structuredAiService.call(userId, MODULE, INSTRUCTION, context,
                ClaimExtractionResult.class, () -> new ClaimExtractionResult(ClaimCandidateExtractor.extract(messages)));

        return sanitize(result, ownedMessageIds);
    }

    /**
     * Enforce the precision-first invariants on any extraction source: a candidate must carry a known
     * claim type, a non-blank value, and at least one provenance id that references a message actually
     * in this conversation. Anything else is dropped rather than trusted.
     */
    private List<ClaimCandidate> sanitize(ClaimExtractionResult result, Set<Long> ownedMessageIds) {
        List<ClaimCandidate> clean = new ArrayList<>();
        for (ClaimCandidate candidate : result.candidates()) {
            if (candidate == null) continue;
            if (!ClaimTypes.ALL.contains(candidate.claimType())) continue;
            if (candidate.value() == null || candidate.value().isBlank()) continue;
            List<Long> provenance = candidate.provenanceMessageIds() == null ? List.of()
                    : candidate.provenanceMessageIds().stream().filter(ownedMessageIds::contains).toList();
            if (provenance.isEmpty()) continue;
            double confidence = Math.max(0.0, Math.min(1.0, candidate.confidence()));
            clean.add(new ClaimCandidate(candidate.claimType(), candidate.claimKey(), candidate.value(),
                    candidate.authorityLevel(), confidence, provenance, candidate.evidenceText(),
                    candidate.uncertain()));
        }
        return clean;
    }
}
