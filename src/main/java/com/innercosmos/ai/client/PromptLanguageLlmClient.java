package com.innercosmos.ai.client;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;

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
        String mode = language.toLowerCase(Locale.ROOT);
        String languageRule;
        if ("auto".equals(mode) || mode.startsWith("auto-") || mode.startsWith("auto:")) {
            languageRule = """
                    Detect the dominant language in the current user's latest meaningful input.
                    Reply naturally in that language: use English for English input and Simplified
                    Chinese for Chinese input. For mixed input, follow the dominant language unless
                    the user explicitly asks for another one. If there is no user-authored language
                    signal (for example, a background task containing only structured data), default
                    to English. Apply the same language to every natural-language JSON value.
                    """;
        } else if (mode.startsWith("en")) {
            languageRule = """
                    Output language: English (Singapore). Use English for every user-visible sentence
                    and every natural-language JSON value.
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
}
