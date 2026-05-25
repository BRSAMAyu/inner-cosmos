package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;

public class LetterCreateRequest {
    public Long receiverUserId;
    public Long receiverCapsuleId;
    @NotBlank(message = "title is required")
    public String title;
    @NotBlank(message = "letterBody is required")
    public String letterBody;
}
