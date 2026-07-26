package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

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

    /**
     * Optional delivery cadence. Omitted requests preserve the existing three-minute journey.
     * CUSTOM additionally requires {@link #customArrivalAt}.
     */
    public LetterDeliveryPreset deliveryPreset;

    /** Absolute arrival intent for CUSTOM, encoded as an ISO-8601 instant (for example ...Z). */
    public Instant customArrivalAt;

    /**
     * IANA timezone used to interpret TONIGHT and TOMORROW. Defaults to Asia/Shanghai for the
     * current classroom product; the resolved arrival itself is persisted in UTC.
     */
    public String timeZone;
}
