package com.innercosmos.common;

public final class ErrorCode {
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String SAFETY_BLOCKED = "SAFETY_BLOCKED";
    public static final String LETTER_STATE_INVALID = "LETTER_STATE_INVALID";
    public static final String AI_PROVIDER_ERROR = "AI_PROVIDER_ERROR";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String CONFLICT = "CONFLICT";
    /**
     * Gemini audit 3.3 (CONFIRMED/P1): the letter contains soft-confirm PII (phone/email/address)
     * and the sender has not yet explicitly confirmed sending it. Distinct from SAFETY_BLOCKED
     * (which is not user-overridable) -- the client should offer a "confirm and send" affordance.
     */
    public static final String PII_CONFIRMATION_REQUIRED = "PII_CONFIRMATION_REQUIRED";

    private ErrorCode() {
    }
}
