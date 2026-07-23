package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;

public class LetterCreateRequest {
    public Long receiverUserId;
    public Long receiverCapsuleId;
    @NotBlank(message = "title is required")
    public String title;
    @NotBlank(message = "letterBody is required")
    public String letterBody;
    /**
     * Gemini audit 1.8 (CONFIRMED/P1): optional client-supplied idempotency key for this compose
     * action. A retried call with the same key (from the same sender) returns the original
     * letter instead of inserting a duplicate.
     */
    public String idempotencyKey;
}
