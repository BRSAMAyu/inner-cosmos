package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class WakeIntentFeedbackRequest {
    @NotBlank
    @Pattern(regexp = "MATCHED|LATER|STOP_SIMILAR")
    public String choice;
}
