package com.innercosmos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "username is required")
    @Size(min = 2, max = 50, message = "username must be 2-50 characters")
    public String username;
    @NotBlank(message = "password is required")
    @Size(min = 8, max = 128, message = "password must be 8-128 characters")
    public String password;
    public String nickname;
    @Email(message = "invalid email format")
    @Size(max = 255)
    public String email;
}
