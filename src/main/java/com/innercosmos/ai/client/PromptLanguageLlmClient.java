package com.innercosmos.ai.client;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the configured product language at the final provider boundary.
 *
 * <p>Inner Cosmos has several independent prompt builders (Aurora, memory extraction, capsules
 * and background reflection). Keeping the language contract here prevents one legacy Chinese
 * instruction from leaking into an otherwise English classroom journey, including routed models
 * and streaming calls.
 */
public final class PromptLanguageLlmClient implements LlmClient {
    static final String MARKER = "[INNER_COSMOS_OUTPUT_LANGUAGE]";
    static final String ENGLISH = "en";
    static final String SIMPLIFIED_CHINESE = "zh-CN";
    static final String AUTO = "auto";
    private static final Pattern EXPLICIT_OUTPUT_LANGUAGE = Pattern.compile(
            "\"outputLanguage\"\\s*:\\s*\"(en|zh-CN|auto)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*");

    private final LlmClient delegate;
    private final String language;

    public PromptLanguageLlmClient(LlmClient delegate, String language) {
        this.delegate = delegate;
        this.language = language == null || language.isBlank() ? "auto" : language.trim();
    }

    @Override
    public String chat(LlmRequest request) {
        enforce(request);
        return delegate.chat(request);
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        enforce(request);
        return delegate.streamChat(request);
    }

    void enforce(LlmRequest request) {
        if (request == null) {
            return;
        }
        if (request.systemPrompt != null && request.systemPrompt.contains(MARKER)) {
            return;
        }
        String explicit = explicitOutputLanguage(request);
        boolean hasExplicitTurnContract = explicit != null;
        String mode = hasExplicitTurnContract
                ? explicit.toLowerCase(Locale.ROOT)
                : language.toLowerCase(Locale.ROOT);
        String languageRule;
        if (mode.startsWith("en")) {
            languageRule = """
                    Output language is English. Use English for every user-visible sentence and
                    every natural-language JSON value, including planner constraints, speaker
                    segments and critic repairs. Do not switch to Chinese because older system
                    instructions, examples, mode labels or structured context happen to be in
                    Chinese. Chinese is allowed only for an exact user quotation or proper noun.
                    """;
        } else if (mode.startsWith("zh")) {
            if (!hasExplicitTurnContract) {
                // Preserve the historical configured-zh behavior outside an Aurora turn. A
                // per-turn outputLanguage contract is what upgrades it to a hard JSON boundary.
                return;
            }
            languageRule = """
                    输出语言为简体中文。所有用户可见句子以及 JSON 中的自然语言值都必须使用简体中文，
                    包括规划约束、Speaker segments 与 critic 修复。不要因为旧提示、示例、模式标签或
                    结构化上下文中出现英文而切换到英文；仅用户原样引用的英文、代码和专有名词除外。
                    """;
        } else if (AUTO.equals(mode) || mode.startsWith("auto-") || mode.startsWith("auto:")) {
            languageRule = """
                    Detect the dominant language in the current user's latest meaningful input.
                    Reply naturally in that language: use English for English input and Simplified
                    Chinese for Chinese input. For mixed input, follow the dominant language unless
                    the user explicitly asks for another one. If there is no user-authored language
                    signal (for example, a background task containing only structured data), default
                    to English. Apply the same language to every natural-language JSON value.
                    """;
        } else {
            // The historical Chinese/default mode is intentionally untouched so this decorator
            // remains backwards compatible outside the Demo profile.
            return;
        }
        String rule = MARKER + "\n" + languageRule + """
                Keep contract-defined JSON keys unchanged. This language rule overrides older
                instructions that hard-code Chinese output.
                """;
        request.systemPrompt = request.systemPrompt == null || request.systemPrompt.isBlank()
                ? rule
                : request.systemPrompt + "\n\n" + rule;
    }

    /**
     * Resolves the language of the latest user-authored message before it is mixed with Chinese
     * planner schemas, mode labels and examples. A clearly monolingual latest message wins over a
     * stale browser locale. Locale is the tie-breaker for genuine code-switching; when it is absent,
     * the dominant script decides.
     */
    public static String normalizeOutputLanguage(String locale, String latestUserMessage) {
        String message = latestUserMessage == null ? "" : latestUserMessage.strip();
        long hanCharacters = message.codePoints()
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                .count();
        Matcher latinMatcher = LATIN_WORD.matcher(message);
        int latinWords = 0;
        while (latinMatcher.find()) {
            latinWords++;
        }

        if (hanCharacters > 0 && latinWords == 0) {
            return SIMPLIFIED_CHINESE;
        }
        if (latinWords > 0 && hanCharacters == 0) {
            return ENGLISH;
        }

        String localeLanguage = normalizeLocale(locale);
        if (hanCharacters > 0 && latinWords > 0) {
            if (!AUTO.equals(localeLanguage)) {
                return localeLanguage;
            }
            if (hanCharacters >= Math.max(4L, latinWords * 2L)) {
                return SIMPLIFIED_CHINESE;
            }
            if (latinWords >= Math.max(4L, hanCharacters)) {
                return ENGLISH;
            }
            return AUTO;
        }
        return localeLanguage;
    }

    private static String normalizeLocale(String locale) {
        String normalized = locale == null ? "" : locale.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("en")) {
            return ENGLISH;
        }
        if (normalized.startsWith("zh")) {
            return SIMPLIFIED_CHINESE;
        }
        return AUTO;
    }

    private static String explicitOutputLanguage(LlmRequest request) {
        String requestJson = request == null || request.requestJson == null
                ? "" : request.requestJson;
        Matcher matcher = EXPLICIT_OUTPUT_LANGUAGE.matcher(requestJson);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        if (value.equalsIgnoreCase(SIMPLIFIED_CHINESE)) {
            return SIMPLIFIED_CHINESE;
        }
        if (value.equalsIgnoreCase(ENGLISH)) {
            return ENGLISH;
        }
        return AUTO;
    }
}
