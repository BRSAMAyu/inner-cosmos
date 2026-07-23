package com.innercosmos.dto;

/**
 * Gemini audit 3.3 (CONFIRMED/P1): optional body for POST /api/letters/{id}/send. {@code
 * confirmPii} lets the sender explicitly confirm sending after being warned that the letter
 * contains soft-confirm PII (phone/email/address) -- never used to override a hard-block
 * (credentials/secrets), which has no confirmation path.
 */
public class LetterSendRequest {
    public Boolean confirmPii;
}
