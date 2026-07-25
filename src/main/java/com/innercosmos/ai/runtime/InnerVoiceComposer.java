package com.innercosmos.ai.runtime;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Composes Aurora's "inner voice" (心声): a short, genuinely distinct first-person interior line
 * derived from the planner kernel's {@code emotionalNeed}/{@code relationshipMove}, surfaced (as
 * text + synthesized audio) alongside -- never instead of -- the visible spoken reply.
 *
 * <p><b>Hard requirement (product intent, not a style preference):</b> the composed line must
 * NEVER restate, paraphrase, or summarize the visible spoken {@code segments}. A real inner
 * monologue is aware of what was just said out loud, but its interior register is categorically
 * different -- otherwise it reads as a lazy echo and breaks the "genuine, alive" feel this
 * feature exists for. See {@code InnerVoiceComposerTest} for the pinned, measurable definition of
 * "distinct" (bounded character-bigram overlap with the spoken segments).
 *
 * <p>This call is always a single, lightweight, best-effort structured-AI call: any exception
 * bubbles to the caller's own try/catch (Aurora's turn stream must never be blocked or failed by
 * inner-voice composition -- see {@code AuroraAgentServiceImpl}'s call site).
 */
@Component
public class InnerVoiceComposer {
    /** Roughly 40 Chinese characters, generous enough for punctuation. */
    private static final int MAX_LENGTH = 40;

    private final StructuredAiService ai;

    public InnerVoiceComposer(StructuredAiService ai) {
        this.ai = ai;
    }

    /**
     * @return the composed inner-voice line, or {@code null} when the plan/spoken reply do not
     *         warrant one (e.g. no discernible emotional need) or the model produced nothing
     *         usable. Callers must treat {@code null} as "omit the inner_voice event" -- never
     *         throw.
     */
    public String compose(Long userId, String mode, StructuredAiResults.AuroraPlanResult plan,
                          StructuredAiResults.AuroraResult spoken, LlmClient client) {
        if (plan == null) return null;
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("emotionalNeed", plan.emotionalNeed);
        context.put("relationshipMove", plan.relationshipMove);
        context.put("innerVoiceSeed", plan.innerVoiceSeed);
        context.put("visibleReply", spoken == null ? List.of() : spoken.segments);

        StructuredAiResults.InnerVoiceResult result = ai.call(userId, "AURORA_INNER_VOICE_" + mode,
            innerVoiceInstruction(), context, StructuredAiResults.InnerVoiceResult.class,
            () -> null, client);

        return normalize(result, spoken);
    }

    private String normalize(StructuredAiResults.InnerVoiceResult result,
                             StructuredAiResults.AuroraResult spoken) {
        if (result == null || result.innerVoiceText == null) return null;
        String text = result.innerVoiceText.strip();
        if (text.isEmpty()) return null;
        if (text.contains("用户") || text.contains("他") || text.contains("她")
                || text.contains("越界") || text.contains("掌控节奏")
                || text.contains("应该") || text.contains("需要让")) return null;
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            // Truncate defensively at a code-point boundary rather than reject outright --
            // an over-long but genuinely distinct line is still better than none.
            int end = text.offsetByCodePoints(0, MAX_LENGTH);
            text = text.substring(0, end);
        }
        String visible = spoken == null || spoken.segments == null ? "" : String.join("", spoken.segments);
        if (charBigramOverlapRatio(text, visible) >= 0.30) return null;
        return text;
    }

    private double charBigramOverlapRatio(String candidate, String reference) {
        Set<String> candidateBigrams = bigrams(candidate);
        Set<String> referenceBigrams = bigrams(reference);
        if (candidateBigrams.isEmpty()) return 0.0;
        long shared = candidateBigrams.stream().filter(referenceBigrams::contains).count();
        return (double) shared / candidateBigrams.size();
    }

    private Set<String> bigrams(String text) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 1 < text.length(); i++) result.add(text.substring(i, i + 2));
        return result;
    }

    private String innerVoiceInstruction() {
        return """
            你是 Aurora 未说出口的内心声音（心声）。只输出严格 JSON，不要 markdown 代码块，
            不输出 JSON 之外的任何文字。必须严格匹配以下字段名和结构（示例）：
            {"innerVoiceText":"一句第一人称内心独白，不超过 40 个汉字"}

            这句内心独白必须由 emotionalNeed、relationshipMove 和 innerVoiceSeed 生发，是 Aurora 自己此刻真实的
            内在感受或念头，绝不是对 visibleReply（已经说出口的话）的复述、改写或总结 --
            即使意思相关也必须换一套完全不同的措辞和视角，读起来像是说出口的话背后那句真正没说出的
            心里话，而不是同一句话的第二个版本。不使用 visibleReply 中出现过的具体词语或短语。
            必须用第一人称“我”的内在感受或微妙矛盾；不要用“他/她/用户”观察或评估对方，不写
            “越界、掌控节奏、应该、需要让”等规则语言，不做心理分析，不像产品策略说明。
            禁止“我会陪着你、我想陪着你、我在这里”这类苍白的陪伴口号。
            没有真实、独特的内心独白可写时（例如情绪平淡、无实质关系动作），
            innerVoiceText 给空字符串 ""。
            """;
    }
}
