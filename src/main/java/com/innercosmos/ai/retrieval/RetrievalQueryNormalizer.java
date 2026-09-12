package com.innercosmos.ai.retrieval;

/**
 * CP-22 task-oriented query normalization. The raw user message is conversational:
 * "帮我分析一下这件事" asks for a service, it does not describe the memory to find.
 * Feeding meta-discourse into the lexical admission gate let service verbs ("分析",
 * "梳理", "聊聊") match unrelated memories whose content merely contains the same verb,
 * admitting them into the Evidence Pack. This normalizer strips a tight list of
 * conversational service markers before scoring, so only content terms drive admission.
 *
 * <p>Deterministic, side-effect free and script-agnostic: pure string work, unit-testable.
 * The Evidence Pack still reports the user's original wording — normalization is a scoring
 * concern, never a user-facing rewrite.
 */
public final class RetrievalQueryNormalizer {

    /**
     * Conversational service markers. Tight by design: each is an unambiguous meta-request
     * ("help me analyze", "let's chat") rather than a content word, so removing it cannot
     * delete the subject the user actually talked about.
     */
    private static final String[] META_MARKERS = {
            "帮我分析", "分析一下", "帮我梳理", "梳理一下", "帮我理清", "理清一下",
            "帮我看看", "怎么看待", "你怎么看", "你怎么理解", "为什么会这样",
            "我想聊聊", "想聊聊", "我想说说", "想说出来", "只是想说", "只想说",
            "随便聊聊", "陪我聊聊", "聊一聊", "我们聊聊", "跟我说说",
            "帮帮我", "谢谢", "在吗", "你好", "晚安", "早上好", "然后呢", "后来呢", "一下"
    };

    private RetrievalQueryNormalizer() {
    }

    /** Strips meta-discourse and collapses whitespace; returns "" when nothing contentful remains. */
    public static String normalize(String query) {
        if (query == null) return "";
        String text = query;
        for (String marker : META_MARKERS) {
            text = text.replace(marker, " ");
        }
        return text.replaceAll("[\\p{P}\\p{S}\\s]+", " ").trim();
    }
}
