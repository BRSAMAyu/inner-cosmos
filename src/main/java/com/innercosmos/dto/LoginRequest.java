package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank(message = "username is required")
    public String username;
    @NotBlank(message = "password is required")
    public String password;
}
