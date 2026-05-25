package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterRequest {
    @NotBlank(message = "username is required")
    public String username;
    @NotBlank(message = "password is required")
    public String password;
    public String nickname;
    public String email;
}
