package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WakeIntentNegotiateRequest {
    @NotBlank @Size(max = 120)
    public String when;
    @NotBlank @Size(max = 160)
    public String purpose;
    @NotBlank @Size(max = 240)
    public String reasonForUser;
    @NotBlank @Size(max = 4000)
    public String content;
    @Size(max = 64)
    public String timezone;
    public Long contextSessionId;
}
