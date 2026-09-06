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
    /**
     * CP-08 adult admission: the 18+ gate rejected the registration or the account is in
     * MINOR_RESTRICTED state. Distinct from FORBIDDEN so clients can show the age-gate
     * explanation, safety resources and the appeal path instead of a generic denial.
     */
    public static final String ADULT_GATE_REQUIRED = "ADULT_GATE_REQUIRED";
    /**
     * CP-07 consent gate: the action needs a consent decision the user has not given
     * (initially: sending content to a real model provider). Distinct from FORBIDDEN so
     * clients can route to the consent center instead of showing a dead end.
     */
    public static final String CONSENT_REQUIRED = "CONSENT_REQUIRED";

    private ErrorCode() {
    }
}
