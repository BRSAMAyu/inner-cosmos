package com.innercosmos.ai.runtime;

import java.util.Locale;

/**
 * A6 user-visible degradation contract. When an Aurora turn fails, the user must get a calm, specific,
 * category-appropriate reply rather than a stack trace or a generic "error" — and that mapping must be
 * testable in isolation (failure injection), not buried in a 1200-line service. {@link #classify}
 * walks the whole cause chain and keys off both the exception TYPE and its message, so a
 * {@code SocketTimeoutException} nested under a client wrapper is still recognised as a timeout.
 *
 * <p>The {@code riskFlag} values are kept identical to the historical inline flags
 * (TIMEOUT / RATE_LIMITED / PARSE_ERROR / NETWORK_ERROR) so downstream risk handling and metrics do
 * not shift. {@code PROVIDER_UNAVAILABLE} is the safe default for anything unrecognised.
 */
public final class AiFailureContract {

    public enum Category {
        TIMEOUT("TIMEOUT",
                "I am still thinking about what you said, but it is taking a while. You can say it again, or we can talk about something else."),
        RATE_LIMITED("RATE_LIMITED",
                "Things are a bit busy on my end. Please wait a minute and try again."),
        MALFORMED_OUTPUT("PARSE_ERROR",
                "I heard you but my thoughts did not come together clearly. Could you try saying it differently?"),
        PROVIDER_UNAVAILABLE("NETWORK_ERROR",
                "I could not reach my thinking just now. I am still here — tell me again in a moment.");

        public final String riskFlag;
        public final String defaultUserMessage;

        Category(String riskFlag, String defaultUserMessage) {
            this.riskFlag = riskFlag;
            this.defaultUserMessage = defaultUserMessage;
        }
    }

    private AiFailureContract() {
    }

    public static Category classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String type = t.getClass().getName().toLowerCase(Locale.ROOT);
            String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
            if (type.contains("timeout") || msg.contains("timeout") || msg.contains("timed out")) {
                return Category.TIMEOUT;
            }
            if (msg.contains("429") || msg.contains("rate_limit") || msg.contains("rate limit")
                    || msg.contains("too many requests")) {
                return Category.RATE_LIMITED;
            }
            if (type.contains("jsonparse") || type.contains("jsonprocessing") || type.contains("jsonmapping")
                    || msg.contains("parse") || msg.contains("json") || msg.contains("malformed")) {
                return Category.MALFORMED_OUTPUT;
            }
        }
        return Category.PROVIDER_UNAVAILABLE;
    }
}
