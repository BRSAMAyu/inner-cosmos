package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class WakeIntentCreateRequest {
    @NotBlank @Size(max = 160)
    public String purpose;
    @NotBlank @Size(max = 240)
    public String reasonForUser;
    @NotBlank @Size(max = 4000)
    public String content;
    @NotNull
    public LocalDateTime preferredAt;
    public LocalDateTime earliestAt;
    public LocalDateTime latestAt;
    @Size(max = 64)
    public String timezone;
}
