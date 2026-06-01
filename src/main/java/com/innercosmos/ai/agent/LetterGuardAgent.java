package com.innercosmos.ai.agent;

import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import org.springframework.stereotype.Component;

@Component
public class LetterGuardAgent {
    private final StructuredAiService structuredAiService;

    public LetterGuardAgent(StructuredAiService structuredAiService) {
        this.structuredAiService = structuredAiService;
    }

    public boolean allow(String text) {
        if (text == null) {
            return true;
        }
        StructuredAiResults.LetterGuardResult result = structuredAiService.call(null, "LETTER_GUARD",
                """
                Return JSON with allow boolean, reason, riskFlags[].
                Block threats, harassment, doxxing, pressure for real identity, diagnosis promises, and coercion.
                Allow ordinary vulnerable or reflective writing.
                """,
                java.util.Map.of("letterText", text),
                StructuredAiResults.LetterGuardResult.class,
                () -> fallback(text));
        return Boolean.TRUE.equals(result.allow);
    }

    private StructuredAiResults.LetterGuardResult fallback(String text) {
        StructuredAiResults.LetterGuardResult result = new StructuredAiResults.LetterGuardResult();
        result.allow = !(text.contains("威胁") || text.contains("骚扰") || text.contains("人肉"));
        result.reason = result.allow ? "passed" : "contains unsafe boundary language";
        return result;
    }
}
