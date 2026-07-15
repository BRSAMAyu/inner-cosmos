package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {
    @NotBlank(message = "username is required")
    public String username;
    @NotBlank(message = "password is required")
    public String password;
    @Size(max = 64)
    public String timezone;
}
