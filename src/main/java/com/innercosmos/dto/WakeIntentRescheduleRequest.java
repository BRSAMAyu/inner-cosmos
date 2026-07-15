package com.innercosmos.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class WakeIntentRescheduleRequest {
    @NotNull
    public LocalDateTime preferredAt;
    public LocalDateTime earliestAt;
    public LocalDateTime latestAt;
}
