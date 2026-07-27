package com.innercosmos.util;

/**
 * Which language a capsule should answer a visitor in.
 *
 * <p>The public demo is used by Chinese-speaking and English-speaking visitors in the same room,
 * and the capsule runtime previously mixed the two: the persona-chat system instruction is written
 * in Chinese, the seeded showcase capsules are written in English, and the identity disclaimer was
 * a hardcoded Chinese sentence appended to every opening reply. The result was a capsule answering
 * an English question in Chinese, or an English answer carrying a Chinese footer.
 *
 * <p>Resolution is deliberately dumb and local: the visitor's own message decides. No provider call,
 * no profile lookup, no request header — the sentence in front of the capsule is the strongest and
 * cheapest signal, and it lets one visitor switch language mid-conversation.
 */
public final class VisitorLanguage {

    public static final String CHINESE = "zh";
    public static final String ENGLISH = "en";

    private VisitorLanguage() {
    }

    /**
     * @return {@link #CHINESE} when the message contains any Han character, else {@link #ENGLISH}.
     *         A blank message resolves to English, matching the classroom UI default.
     */
    public static String detect(String message) {
        if (message == null || message.isBlank()) return ENGLISH;
        for (int i = 0; i < message.length(); ) {
            int codePoint = message.codePointAt(i);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                return CHINESE;
            }
            i += Character.charCount(codePoint);
        }
        return ENGLISH;
    }

    public static boolean isChinese(String language) {
        return CHINESE.equals(language);
    }

    /** Picks the Chinese or English variant of a runtime-owned (non-model) sentence. */
    public static String pick(String language, String chinese, String english) {
        return isChinese(language) ? chinese : english;
    }
}
